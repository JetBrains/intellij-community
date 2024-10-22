// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightingLevelManager;
import com.intellij.codeInspection.InspectionProfile;
import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.codeInspection.ex.InspectionProfileWrapper;
import com.intellij.diagnostic.PluginException;
import com.intellij.lang.ExternalLanguageAnnotators;
import com.intellij.lang.LangBundle;
import com.intellij.lang.annotation.ExternalAnnotator;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Attachment;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.editor.ex.MarkupModelEx;
import com.intellij.openapi.editor.impl.DocumentMarkupModel;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.util.BackgroundTaskUtil;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.util.ProperTextRange;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiFile;
import com.intellij.util.TimeoutUtil;
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.update.Update;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

@ApiStatus.Internal
public final class ExternalToolPass extends ProgressableTextEditorHighlightingPass implements DumbAware {
  private static final Logger LOG = Logger.getInstance(ExternalToolPass.class);

  private final List<MyData<?,?>> myAnnotationData = Collections.synchronizedList(new ArrayList<>());
  private volatile @NotNull List<? extends HighlightInfo> myHighlightInfos = Collections.emptyList();
  private volatile boolean externalUpdateTaskCompleted; // true when the task in ExternalAnnotatorManager.getInstance().queue() was completed

  private static final class MyData<K,V> {
    final @NotNull ExternalAnnotator<K,V> annotator;
    final @NotNull PsiFile psiRoot;
    final @NotNull K collectedInfo;
    volatile V annotationResult;
    volatile AnnotationHolderImpl annotationHolder;

    MyData(@NotNull ExternalAnnotator<K,V> annotator, @NotNull PsiFile psiRoot, @NotNull K collectedInfo) {
      this.annotator = annotator;
      this.psiRoot = psiRoot;
      this.collectedInfo = collectedInfo;
    }
  }

  ExternalToolPass(@NotNull PsiFile file,
                   @NotNull Document document,
                   @Nullable Editor editor,
                   int startOffset,
                   int endOffset,
                   @NotNull HighlightInfoProcessor processor) {
    super(file.getProject(), document, LangBundle.message("pass.external.annotators"), file, editor, new TextRange(startOffset, endOffset), false, processor);
  }

  @Override
  protected void collectInformationWithProgress(@NotNull ProgressIndicator progress) {
    FileViewProvider viewProvider = myFile.getViewProvider();
    HighlightingLevelManager highlightingManager = HighlightingLevelManager.getInstance(myProject);
    Map<PsiFile, List<ExternalAnnotator<?,?>>> allAnnotators = new HashMap<>();
    int externalAnnotatorsInRoots = 0;
    InspectionProfileImpl currentProfile = InspectionProjectProfileManager.getInstance(myProject).getCurrentProfile();
    Function<? super InspectionProfile, ? extends InspectionProfileWrapper> custom = InspectionProfileWrapper.getCustomInspectionProfileWrapper(myFile);
    InspectionProfile profile;
    if (custom != null) {
      profile = custom.apply(currentProfile).getInspectionProfile();
    }
    else {
      profile = currentProfile;
    }
    for (PsiFile psiRoot : viewProvider.getAllFiles()) {
      if (highlightingManager.shouldInspect(psiRoot) && !highlightingManager.runEssentialHighlightingOnly(psiRoot)) {
        List<ExternalAnnotator<?,?>> annotators = ExternalLanguageAnnotators.allForFile(psiRoot.getLanguage(), psiRoot);
        annotators = ContainerUtil.filter(annotators, annotator -> {
          String shortName = annotator.getPairedBatchInspectionShortName();
          if (shortName != null) {
            HighlightDisplayKey key = HighlightDisplayKey.find(shortName);
            if (key == null) {
              if (!ApplicationManager.getApplication().isUnitTestMode()) {
                // tests should care about registering corresponding paired tools
                processError(new Exception("Paired tool '" + shortName + "' not found"), annotator, psiRoot);
              }
              return false;
            }
            return profile.isToolEnabled(key, myFile);
          }
          return true;
        });
        if (!annotators.isEmpty()) {
          externalAnnotatorsInRoots += annotators.size();
          allAnnotators.put(psiRoot, annotators);
        }
      }
    }
    setProgressLimit(externalAnnotatorsInRoots);

    boolean errorFound = DaemonCodeAnalyzerEx.getInstanceEx(myProject).getFileStatusMap().wasErrorFound(myDocument);
    Editor editor = getEditor();

    DumbService dumbService = DumbService.getInstance(myProject);
    for (Map.Entry<PsiFile, List<ExternalAnnotator<?,?>>> entry : allAnnotators.entrySet()) {
      PsiFile psiRoot = entry.getKey();
      List<ExternalAnnotator<?,?>> annotators = entry.getValue();
      for (ExternalAnnotator<?,?> annotator : annotators) {
        progress.checkCanceled();

        if (!dumbService.isUsableInCurrentContext(annotator)) {
          continue;
        }
        Object collectedInfo = null;
        try {
          collectedInfo = editor == null ? annotator.collectInformation(psiRoot) : annotator.collectInformation(psiRoot, editor, errorFound);
        }
        catch (IndexNotReadyException ignore) {
        }
        catch (Throwable t) {
          processError(t, annotator, psiRoot);
        }

        if (collectedInfo != null) {
          //noinspection unchecked,rawtypes
          myAnnotationData.add(new MyData(annotator, psiRoot, collectedInfo));
        }
        advanceProgress(1);
      }
    }

    long modificationStampBefore = myDocument.getModificationStamp();
    ExternalAnnotatorManager.getInstance().queue(new Update(myFile) {
      @Override
      public void setRejected() {
        try {
          super.setRejected();
          if (!myProject.isDisposed()) { // Project close in EDT might call MergeUpdateQueue.dispose which calls setRejected in EDT
            doFinish();
          }
        }
        finally {
          externalUpdateTaskCompleted = true;
        }
      }

      @Override
      public void run() {
        try {
          if (documentChanged(modificationStampBefore) || myProject.isDisposed()) {
            return;
          }

          // have to instantiate new indicator because the old one (progress) might have already been canceled
          DaemonProgressIndicator indicator = new DaemonProgressIndicator();
          BackgroundTaskUtil.runUnderDisposeAwareIndicator(myProject, () -> {
            // run annotators outside the read action because they could start OSProcessHandler
            runChangeAware(myDocument, () -> doAnnotate());
            ReadAction.run(() -> {
              ProgressManager.checkCanceled();
              if (!documentChanged(modificationStampBefore)) {
                doApply();
                doFinish();
              }
            });
          }, indicator);
        }
        finally {
          externalUpdateTaskCompleted = true;
        }
      }
    });
  }

  @Override
  public @NotNull List<HighlightInfo> getInfos() {
    ApplicationManager.getApplication().assertIsNonDispatchThread();
    long deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(60);

    while (!externalUpdateTaskCompleted) {
      ProgressManager.checkCanceled();
      TimeoutUtil.sleep(1);
      Thread.yield();
      if (System.currentTimeMillis() > deadline) break;
    }
    //noinspection unchecked
    return (List<HighlightInfo>)myHighlightInfos;
  }

  @Override
  protected void applyInformationWithProgress() {
  }

  private boolean documentChanged(long modificationStampBefore) {
    return myDocument.getModificationStamp() != modificationStampBefore;
  }

  private void doAnnotate() {
    for (MyData<?,?> data : myAnnotationData) {
      doAnnotate(data);
    }
  }

  private static <K, V> void doAnnotate(@NotNull MyData<K, V> data) {
    try {
      AnnotationSessionImpl.computeWithSession(data.psiRoot, false, data.annotator, annotationHolder -> {
        data.annotationHolder = (AnnotationHolderImpl)annotationHolder;
        data.annotationResult = data.annotator.doAnnotate(data.collectedInfo);
        return null;
      });
    }
    catch (IndexNotReadyException ignore) {
    }
    catch (Throwable t) {
      processError(t, data.annotator, data.psiRoot);
    }
  }

  private void doApply() {
    for (MyData<?,?> data : myAnnotationData) {
      doApply(data);
    }
  }

  private static <K, V> void doApply(@NotNull MyData<K, V> data) {
    if (data.annotationResult != null && data.psiRoot.isValid()) {
      try {
        data.annotationHolder.applyExternalAnnotatorWithContext(data.psiRoot, data.annotationResult);
      }
      catch (Throwable t) {
        processError(t, data.annotator, data.psiRoot);
      }
    }
  }

  @RequiresBackgroundThread
  private void doFinish() {
    List<HighlightInfo> highlights = myAnnotationData.stream()
      .flatMap(data ->
        ContainerUtil.notNullize(data.annotationHolder).stream().map(annotation -> HighlightInfo.fromAnnotation(data.annotator, annotation)))
      .toList();
    MarkupModelEx markupModel = (MarkupModelEx)DocumentMarkupModel.forDocument(myDocument, myProject, true);
    HighlightingSessionImpl.runInsideHighlightingSession(myFile, getColorsScheme(), ProperTextRange.create(myFile.getTextRange()), false, session -> {
      // use the method which doesn't retrieve a HighlightingSession from the indicator, because we likely destroyed the one already
      BackgroundUpdateHighlightersUtil.setHighlightersInRange(myRestrictRange, highlights, markupModel, getId(), session);
      DaemonCodeAnalyzerEx.getInstanceEx(myProject).getFileStatusMap().markFileUpToDate(myDocument, getId());
      myHighlightInfos = highlights;
    });
  }

  private static void processError(@NotNull Throwable t, @NotNull ExternalAnnotator<?,?> annotator, @NotNull PsiFile root) {
    if (t instanceof ProcessCanceledException pce) throw pce;

    VirtualFile file = root.getVirtualFile();
    String path = file != null ? file.getPath() : root.getName();

    String message = "annotator: " + annotator + " (" + annotator.getClass() + ")";
    PluginException pe = PluginException.createByClass(message, t, annotator.getClass());
    LOG.error("ExternalToolPass: ", pe, new Attachment("root_path.txt", path));
  }

  private static void runChangeAware(@NotNull Document document, @NotNull Runnable runnable) {
    ProgressIndicator currentIndicator = ProgressManager.getInstance().getProgressIndicator();
    assert currentIndicator != null;
    DocumentListener cancellingListener = new DocumentListener() {
      @Override
      public void documentChanged(@NotNull DocumentEvent event) {
        currentIndicator.cancel();
      }
    };
    document.addDocumentListener(cancellingListener);
    try {
      runnable.run();
    }
    finally {
      document.removeDocumentListener(cancellingListener);
    }
  }
}