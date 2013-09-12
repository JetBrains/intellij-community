/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeHighlighting.BackgroundEditorHighlighter;
import com.intellij.codeHighlighting.HighlightingPass;
import com.intellij.codeHighlighting.Pass;
import com.intellij.codeHighlighting.TextEditorHighlightingPass;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzerSettings;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzerSettingsImpl;
import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.daemon.ReferenceImporter;
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.intention.impl.FileLevelIntentionComponent;
import com.intellij.codeInsight.intention.impl.IntentionHintComponent;
import com.intellij.concurrency.Job;
import com.intellij.ide.PowerSaveMode;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.components.NamedComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.ex.RangeHighlighterEx;
import com.intellij.openapi.editor.impl.DocumentMarkupModel;
import com.intellij.openapi.editor.markup.MarkupModel;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.packageDependencies.DependencyValidationManager;
import com.intellij.psi.PsiCompiledElement;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.PsiDocumentManagerImpl;
import com.intellij.psi.search.scope.packageSet.NamedScopeManager;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.Alarm;
import com.intellij.util.CommonProcessors;
import com.intellij.util.Processor;
import com.intellij.util.SmartList;
import com.intellij.util.ui.UIUtil;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.*;

/**
 * This class also controls the auto-reparse and auto-hints.
 */
public class DaemonCodeAnalyzerImpl extends DaemonCodeAnalyzerEx implements JDOMExternalizable, NamedComponent {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl");

  private static final Key<List<LineMarkerInfo>> MARKERS_IN_EDITOR_DOCUMENT_KEY = Key.create("MARKERS_IN_EDITOR_DOCUMENT");
  private static final Key<List<HighlightInfo>> FILE_LEVEL_HIGHLIGHTS = Key.create("FILE_LEVEL_HIGHLIGHTS");
  private final Project myProject;
  private final DaemonCodeAnalyzerSettings mySettings;
  @NotNull private final EditorTracker myEditorTracker;
  private DaemonProgressIndicator myUpdateProgress = new DaemonProgressIndicator(); //guarded by this

  private final Runnable myUpdateRunnable = createUpdateRunnable();

  private final Alarm myAlarm = new Alarm();
  private boolean myUpdateByTimerEnabled = true;
  private final Collection<VirtualFile> myDisabledHintsFiles = new THashSet<VirtualFile>();
  private final Collection<VirtualFile> myDisabledHighlightingFiles = new THashSet<VirtualFile>();

  private final FileStatusMap myFileStatusMap;
  private DaemonCodeAnalyzerSettings myLastSettings;

  private volatile IntentionHintComponent myLastIntentionHint;
  private volatile boolean myDisposed;     // the only possible transition: false -> true
  private volatile boolean myInitialized;  // the only possible transition: false -> true

  @NonNls private static final String DISABLE_HINTS_TAG = "disable_hints";
  @NonNls private static final String FILE_TAG = "file";
  @NonNls private static final String URL_ATT = "url";
  private final PassExecutorService myPassExecutorService;

  private volatile boolean allowToInterrupt = true;

  public DaemonCodeAnalyzerImpl(@NotNull Project project,
                                @NotNull DaemonCodeAnalyzerSettings daemonCodeAnalyzerSettings,
                                @NotNull EditorTracker editorTracker,
                                @NotNull final NamedScopeManager namedScopeManager,
                                @NotNull final DependencyValidationManager dependencyValidationManager) {
    myProject = project;

    mySettings = daemonCodeAnalyzerSettings;
    myEditorTracker = editorTracker;
    myLastSettings = ((DaemonCodeAnalyzerSettingsImpl)daemonCodeAnalyzerSettings).clone();

    myFileStatusMap = new FileStatusMap(myProject);
    myPassExecutorService = new PassExecutorService(myProject) {
      @Override
      protected void afterApplyInformationToEditor(final TextEditorHighlightingPass pass,
                                                   @NotNull final FileEditor fileEditor,
                                                   final ProgressIndicator updateProgress) {
        if (fileEditor instanceof TextEditor) {
          log(updateProgress, pass, "Apply ");
        }
      }

      @Override
      protected boolean isDisposed() {
        return myDisposed || super.isDisposed();
      }
    };
    Disposer.register(project, myPassExecutorService);
    Disposer.register(project, myFileStatusMap);
    DaemonProgressIndicator.setDebug(LOG.isDebugEnabled());

    assert !myInitialized : "Double Initializing";
    Disposer.register(myProject, new StatusBarUpdater(myProject));

    myInitialized = true;
    myDisposed = false;
    myFileStatusMap.markAllFilesDirty();
    Disposer.register(project, new Disposable() {
      @Override
      public void dispose() {
        assert myInitialized : "Disposing not initialized component";
        assert !myDisposed : "Double dispose";

        stopProcess(false, "Dispose");

        myDisposed = true;
        myLastSettings = null;
      }
    });
  }

  @NotNull
  @TestOnly
  public static List<HighlightInfo> getHighlights(@NotNull Document document, HighlightSeverity minSeverity, @NotNull Project project) {
    List<HighlightInfo> infos = new ArrayList<HighlightInfo>();
    processHighlights(document, project, minSeverity, 0, document.getTextLength(),
                      new CommonProcessors.CollectProcessor<HighlightInfo>(infos));
    return infos;
  }

  @Override
  @NotNull
  @TestOnly
  public List<HighlightInfo> getFileLevelHighlights(@NotNull Project project, @NotNull PsiFile file) {
    VirtualFile vFile = file.getViewProvider().getVirtualFile();
    final FileEditorManager manager = FileEditorManager.getInstance(project);
    List<HighlightInfo> result = new ArrayList<HighlightInfo>();
    for (FileEditor fileEditor : manager.getEditors(vFile)) {
      final List<HighlightInfo> infos = fileEditor.getUserData(FILE_LEVEL_HIGHLIGHTS);
      if (infos == null) continue;
      for (HighlightInfo info : infos) {
          result.add(info);
      }
    }
    return result;
  }

  @Override
  public void cleanFileLevelHighlights(@NotNull Project project, final int group, PsiFile psiFile) {
    if (psiFile == null || !psiFile.getViewProvider().isPhysical()) return;
    VirtualFile vFile = psiFile.getViewProvider().getVirtualFile();
    final FileEditorManager manager = FileEditorManager.getInstance(project);
    for (FileEditor fileEditor : manager.getEditors(vFile)) {
      final List<HighlightInfo> infos = fileEditor.getUserData(FILE_LEVEL_HIGHLIGHTS);
      if (infos == null) continue;
      List<HighlightInfo> infosToRemove = new ArrayList<HighlightInfo>();
      for (HighlightInfo info : infos) {
        if (info.getGroup() == group) {
          manager.removeTopComponent(fileEditor, info.fileLevelComponent);
          infosToRemove.add(info);
        }
      }
      infos.removeAll(infosToRemove);
    }
  }

  @Override
  public void addFileLevelHighlight(@NotNull final Project project,
                                    final int group,
                                    @NotNull final HighlightInfo info,
                                    @NotNull final PsiFile psiFile) {
    VirtualFile vFile = psiFile.getViewProvider().getVirtualFile();
    final FileEditorManager manager = FileEditorManager.getInstance(project);
    for (FileEditor fileEditor : manager.getEditors(vFile)) {
      if (fileEditor instanceof TextEditor) {
        FileLevelIntentionComponent component = new FileLevelIntentionComponent(info.getDescription(), info.getSeverity(), info.quickFixActionRanges,
                                                                                project, psiFile, ((TextEditor)fileEditor).getEditor());
        manager.addTopComponent(fileEditor, component);
        List<HighlightInfo> fileLevelInfos = fileEditor.getUserData(FILE_LEVEL_HIGHLIGHTS);
        if (fileLevelInfos == null) {
          fileLevelInfos = new ArrayList<HighlightInfo>();
          fileEditor.putUserData(FILE_LEVEL_HIGHLIGHTS, fileLevelInfos);
        }
        info.fileLevelComponent = component;
        info.setGroup(group);
        fileLevelInfos.add(info);
      }
    }
  }

  @Override
  @NotNull
  public List<HighlightInfo> runMainPasses(@NotNull PsiFile psiFile,
                                           @NotNull Document document,
                                           @NotNull final ProgressIndicator progress) {
    final List<HighlightInfo> result = new ArrayList<HighlightInfo>();
    final VirtualFile virtualFile = psiFile.getVirtualFile();
    if (virtualFile != null && !virtualFile.getFileType().isBinary()) {
      List<TextEditorHighlightingPass> passes =
        TextEditorHighlightingPassRegistrarEx.getInstanceEx(myProject).instantiateMainPasses(psiFile, document,
                                                                                             HighlightInfoProcessor.getEmpty());

      Collections.sort(passes, new Comparator<TextEditorHighlightingPass>() {
        @Override
        public int compare(TextEditorHighlightingPass o1, TextEditorHighlightingPass o2) {
          if (o1 instanceof GeneralHighlightingPass) return -1;
          if (o2 instanceof GeneralHighlightingPass) return 1;
          return 0;
        }
      });

      for (TextEditorHighlightingPass pass : passes) {
        pass.doCollectInformation(progress);
        result.addAll(pass.getInfos());
      }
    }

    return result;
  }

  @NotNull
  @TestOnly
  public List<HighlightInfo> runPasses(@NotNull PsiFile file,
                                       @NotNull Document document,
                                       @NotNull TextEditor textEditor,
                                       @NotNull int[] toIgnore,
                                       boolean canChangeDocument,
                                       @Nullable Runnable callbackWhileWaiting) throws ProcessCanceledException {
    assert myInitialized;
    assert !myDisposed;
    Application application = ApplicationManager.getApplication();
    application.assertIsDispatchThread();
    if (application.isWriteAccessAllowed()) {
      throw new AssertionError("Must not start highlighting from within write action, or deadlock is imminent");
    }

    // pump first so that queued event do not interfere
    UIUtil.dispatchAllInvocationEvents();
    UIUtil.dispatchAllInvocationEvents();

    Project project = file.getProject();
    setUpdateByTimerEnabled(false);
    FileStatusMap fileStatusMap = getFileStatusMap();
    for (int ignoreId : toIgnore) {
      fileStatusMap.markFileUpToDate(document, ignoreId);
    }
    fileStatusMap.allowDirt(canChangeDocument);

    TextEditorBackgroundHighlighter highlighter = (TextEditorBackgroundHighlighter)textEditor.getBackgroundHighlighter();
    final List<TextEditorHighlightingPass> passes = highlighter.getPasses(toIgnore);
    HighlightingPass[] array = passes.toArray(new HighlightingPass[passes.size()]);
    assert array.length != 0 : "Highlighting is disabled for the file " + file;

    final DaemonProgressIndicator progress = createUpdateProgress();
    myPassExecutorService.submitPasses(Collections.singletonMap((FileEditor)textEditor, array), progress, Job.DEFAULT_PRIORITY);
    try {
      while (progress.isRunning()) {
        try {
          progress.checkCanceled();
          if (callbackWhileWaiting != null) {
            callbackWhileWaiting.run();
          }
          myPassExecutorService.waitFor(50);
          UIUtil.dispatchAllInvocationEvents();
          Throwable savedException = PassExecutorService.getSavedException(progress);
          if (savedException != null) throw savedException;
        }
        catch (RuntimeException e) {
          throw e;
        }
        catch (Error e) {
          throw e;
        }
        catch (Throwable e) {
          throw new RuntimeException(e);
        }
      }
      UIUtil.dispatchAllInvocationEvents();
      UIUtil.dispatchAllInvocationEvents();

      List<HighlightInfo> highlights = getHighlights(document, null, project);
      return highlights;
    }
    finally {
      fileStatusMap.allowDirt(true);
      waitForTermination();
    }
  }

  @TestOnly
  public void prepareForTest() {
    //if (!myInitialized) {
    //  projectOpened();
    //}
    setUpdateByTimerEnabled(false);
    waitForTermination();
  }

  @TestOnly
  public void cleanupAfterTest() {
    if (!myProject.isOpen()) return;
    setUpdateByTimerEnabled(false);
    waitForTermination();
  }

  void waitForTermination() {
    myPassExecutorService.cancelAll(true);
  }

  @Override
  @NotNull
  public String getComponentName() {
    return "DaemonCodeAnalyzer";
  }

  @Override
  public void settingsChanged() {
    DaemonCodeAnalyzerSettings settings = DaemonCodeAnalyzerSettings.getInstance();
    if (settings.isCodeHighlightingChanged(myLastSettings)) {
      restart();
    }
    myLastSettings = ((DaemonCodeAnalyzerSettingsImpl)settings).clone();
  }

  @Override
  public void updateVisibleHighlighters(@NotNull Editor editor) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    // no need, will not work anyway
  }

  @Override
  public void setUpdateByTimerEnabled(boolean value) {
    myUpdateByTimerEnabled = value;
    stopProcess(value, "Update by timer change");
  }

  private int myDisableCount = 0;

  @Override
  public void disableUpdateByTimer(@NotNull Disposable parentDisposable) {
    setUpdateByTimerEnabled(false);
    myDisableCount++;
    ApplicationManager.getApplication().assertIsDispatchThread();

    Disposer.register(parentDisposable, new Disposable() {
      @Override
      public void dispose() {
        myDisableCount--;
        if (myDisableCount == 0) {
          setUpdateByTimerEnabled(true);
        }
      }
    });
  }

  public boolean isUpdateByTimerEnabled() {
    return myUpdateByTimerEnabled;
  }

  @Override
  public void setImportHintsEnabled(@NotNull PsiFile file, boolean value) {
    VirtualFile vFile = file.getVirtualFile();
    if (value) {
      myDisabledHintsFiles.remove(vFile);
      stopProcess(true, "Import hints change");
    }
    else {
      myDisabledHintsFiles.add(vFile);
      HintManager.getInstance().hideAllHints();
    }
  }

  @Override
  public void resetImportHintsEnabledForProject() {
    myDisabledHintsFiles.clear();
  }

  @Override
  public void setHighlightingEnabled(@NotNull PsiFile file, boolean value) {
    VirtualFile virtualFile = PsiUtilCore.getVirtualFile(file);
    if (value) {
      myDisabledHighlightingFiles.remove(virtualFile);
    }
    else {
      myDisabledHighlightingFiles.add(virtualFile);
    }
  }

  @Override
  public boolean isHighlightingAvailable(@Nullable PsiFile file) {
    if (file == null || !file.isPhysical()) return false;
    if (myDisabledHighlightingFiles.contains(PsiUtilCore.getVirtualFile(file))) return false;

    if (file instanceof PsiCompiledElement) return false;
    final FileType fileType = file.getFileType();

    // To enable T.O.D.O. highlighting
    return !fileType.isBinary();
  }

  @Override
  public boolean isImportHintsEnabled(@NotNull PsiFile file) {
    return isAutohintsAvailable(file) && !myDisabledHintsFiles.contains(file.getVirtualFile());
  }

  @Override
  public boolean isAutohintsAvailable(PsiFile file) {
    return isHighlightingAvailable(file) && !(file instanceof PsiCompiledElement);
  }

  @Override
  public void restart() {
    myFileStatusMap.markAllFilesDirty();
    stopProcess(true, "Global restart");
  }

  @Override
  public void restart(@NotNull PsiFile file) {
    Document document = PsiDocumentManager.getInstance(myProject).getCachedDocument(file);
    if (document == null) return;
    myFileStatusMap.markFileScopeDirty(document, new TextRange(0, document.getTextLength()), file.getTextLength());
    stopProcess(true, "Psi file restart");
  }

  @NotNull
  List<TextEditorHighlightingPass> getPassesToShowProgressFor(Document document) {
    List<TextEditorHighlightingPass> allPasses = myPassExecutorService.getAllSubmittedPasses();
    List<TextEditorHighlightingPass> result = new ArrayList<TextEditorHighlightingPass>(allPasses.size());
    for (TextEditorHighlightingPass pass : allPasses) {
      if (pass.getDocument() == document || pass.getDocument() == null) {
        result.add(pass);
      }
    }
    return result;
  }

  boolean isAllAnalysisFinished(@NotNull PsiFile file) {
    if (myDisposed) return false;
    Document document = PsiDocumentManager.getInstance(myProject).getCachedDocument(file);
    return document != null &&
           document.getModificationStamp() == file.getViewProvider().getModificationStamp() &&
           myFileStatusMap.allDirtyScopesAreNull(document);
  }

  @Override
  public boolean isErrorAnalyzingFinished(@NotNull PsiFile file) {
    if (myDisposed) return false;
    Document document = PsiDocumentManager.getInstance(myProject).getCachedDocument(file);
    return document != null &&
           document.getModificationStamp() == file.getViewProvider().getModificationStamp() &&
           myFileStatusMap.getFileDirtyScope(document, Pass.UPDATE_ALL) == null;
  }

  @Override
  @NotNull
  public FileStatusMap getFileStatusMap() {
    return myFileStatusMap;
  }

  synchronized boolean isRunning() {
    return myUpdateProgress != null && !myUpdateProgress.isCanceled();
  }

  synchronized void stopProcess(boolean toRestartAlarm, @NonNls String reason) {
    if (!allowToInterrupt) throw new RuntimeException("Cannot interrupt daemon");

    cancelUpdateProgress(toRestartAlarm, reason);
    myAlarm.cancelAllRequests();
    boolean restart = toRestartAlarm && !myDisposed && myInitialized;
    if (restart) {
      UIUtil.invokeLaterIfNeeded(new Runnable() {
        @Override
        public void run() {
          myAlarm.addRequest(myUpdateRunnable, mySettings.AUTOREPARSE_DELAY);
        }
      });
    }
  }

  private synchronized void cancelUpdateProgress(final boolean start, @NonNls String reason) {
    PassExecutorService.log(myUpdateProgress, null, "CancelX", reason, start);

    if (myUpdateProgress != null) {
      myUpdateProgress.cancel();
      myPassExecutorService.cancelAll(false);
      myUpdateProgress = null;
    }
  }


  public static boolean processHighlightsNearOffset(@NotNull Document document,
                                                    @NotNull Project project,
                                                    @NotNull final HighlightSeverity minSeverity,
                                                    final int offset,
                                                    final boolean includeFixRange,
                                                    @NotNull final Processor<HighlightInfo> processor) {
    return processHighlights(document, project, null, 0, document.getTextLength(), new Processor<HighlightInfo>() {
      @Override
      public boolean process(@NotNull HighlightInfo info) {
        if (!isOffsetInsideHighlightInfo(offset, info, includeFixRange)) return true;

        int compare = info.getSeverity().compareTo(minSeverity);
        return compare < 0 || processor.process(info);
      }
    });
  }

  @Nullable
  public HighlightInfo findHighlightByOffset(@NotNull Document document, final int offset, final boolean includeFixRange) {
    return findHighlightByOffset(document, offset, includeFixRange, HighlightSeverity.INFORMATION);
  }

  @Nullable
  public HighlightInfo findHighlightByOffset(@NotNull Document document,
                                             final int offset,
                                             final boolean includeFixRange,
                                             @NotNull HighlightSeverity minSeverity) {
    final List<HighlightInfo> foundInfoList = new SmartList<HighlightInfo>();
    processHighlightsNearOffset(document, myProject, minSeverity, offset, includeFixRange,
                                new Processor<HighlightInfo>() {
                                  @Override
                                  public boolean process(@NotNull HighlightInfo info) {
                                    if (!foundInfoList.isEmpty()) {
                                      HighlightInfo foundInfo = foundInfoList.get(0);
                                      int compare = foundInfo.getSeverity().compareTo(info.getSeverity());
                                      if (compare < 0) {
                                        foundInfoList.clear();
                                      }
                                      else if (compare > 0) {
                                        return true;
                                      }
                                    }
                                    foundInfoList.add(info);
                                    return true;
                                  }
                                });

    if (foundInfoList.isEmpty()) return null;
    if (foundInfoList.size() == 1) return foundInfoList.get(0);
    return new HighlightInfoComposite(foundInfoList);
  }

  private static boolean isOffsetInsideHighlightInfo(int offset, @NotNull HighlightInfo info, boolean includeFixRange) {
    RangeHighlighterEx highlighter = info.highlighter;
    if (highlighter == null || !highlighter.isValid()) return false;
    int startOffset = highlighter.getStartOffset();
    int endOffset = highlighter.getEndOffset();
    if (startOffset <= offset && offset <= endOffset) {
      return true;
    }
    if (!includeFixRange) return false;
    RangeMarker fixMarker = info.fixMarker;
    if (fixMarker != null) {  // null means its range is the same as highlighter
      if (!fixMarker.isValid()) return false;
      startOffset = fixMarker.getStartOffset();
      endOffset = fixMarker.getEndOffset();
      return startOffset <= offset && offset <= endOffset;
    }
    return false;
  }

  @Nullable
  public static List<LineMarkerInfo> getLineMarkers(@NotNull Document document, Project project) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    MarkupModel markup = DocumentMarkupModel.forDocument(document, project, true);
    return markup.getUserData(MARKERS_IN_EDITOR_DOCUMENT_KEY);
  }

  static void setLineMarkers(@NotNull Document document, List<LineMarkerInfo> lineMarkers, Project project) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    MarkupModel markup = DocumentMarkupModel.forDocument(document, project, true);
    markup.putUserData(MARKERS_IN_EDITOR_DOCUMENT_KEY, lineMarkers);
  }

  void setLastIntentionHint(@NotNull Project project,
                            @NotNull PsiFile file,
                            @NotNull Editor editor,
                            @NotNull ShowIntentionsPass.IntentionsInfo intentions,
                            boolean hasToRecreate) {
    if (!editor.getSettings().isShowIntentionBulb()) {
      return;
    }
    ApplicationManager.getApplication().assertIsDispatchThread();
    hideLastIntentionHint();
    IntentionHintComponent hintComponent = IntentionHintComponent.showIntentionHint(project, file, editor, intentions, false);
    if (hasToRecreate) {
      hintComponent.recreate();
    }
    myLastIntentionHint = hintComponent;
  }

  void hideLastIntentionHint() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    IntentionHintComponent hint = myLastIntentionHint;
    if (hint != null && hint.isVisible()) {
      hint.hide();
      myLastIntentionHint = null;
    }
  }

  @Nullable
  IntentionHintComponent getLastIntentionHint() {
    return myLastIntentionHint;
  }

  @Override
  public void writeExternal(@NotNull Element parentNode) throws WriteExternalException {
    Element disableHintsElement = new Element(DISABLE_HINTS_TAG);
    parentNode.addContent(disableHintsElement);

    List<String> array = new ArrayList<String>();
    for (VirtualFile file : myDisabledHintsFiles) {
      if (file.isValid()) {
        array.add(file.getUrl());
      }
    }
    Collections.sort(array);

    for (String url : array) {
      Element fileElement = new Element(FILE_TAG);
      fileElement.setAttribute(URL_ATT, url);
      disableHintsElement.addContent(fileElement);
    }
  }

  @Override
  public void readExternal(@NotNull Element parentNode) throws InvalidDataException {
    myDisabledHintsFiles.clear();

    Element element = parentNode.getChild(DISABLE_HINTS_TAG);
    if (element != null) {
      for (Object o : element.getChildren(FILE_TAG)) {
        Element e = (Element)o;

        String url = e.getAttributeValue(URL_ATT);
        if (url != null) {
          VirtualFile file = VirtualFileManager.getInstance().findFileByUrl(url);
          if (file != null) {
            myDisabledHintsFiles.add(file);
          }
        }
      }
    }
  }

  @NotNull
  private Runnable createUpdateRunnable() {
    return new Runnable() {
      @Override
      public void run() {
        if (myDisposed || !myProject.isInitialized()) return;
        if (PowerSaveMode.isEnabled()) return;
        Editor activeEditor = FileEditorManager.getInstance(myProject).getSelectedTextEditor();

        final PsiDocumentManagerImpl documentManager = (PsiDocumentManagerImpl)PsiDocumentManager.getInstance(myProject);
        Runnable runnable = new Runnable() {
          @Override
          public void run() {
            PassExecutorService.log(getUpdateProgress(), null, "Update Runnable. myUpdateByTimerEnabled:",
                                    myUpdateByTimerEnabled, " something disposed:",
                                    PowerSaveMode.isEnabled() || myDisposed || !myProject.isInitialized(), " activeEditors:",
                                    myProject.isDisposed() ? null : getSelectedEditors());
            if (!myUpdateByTimerEnabled) return;
            if (myDisposed) return;
            ApplicationManager.getApplication().assertIsDispatchThread();

            final Collection<FileEditor> activeEditors = getSelectedEditors();
            if (activeEditors.isEmpty()) return;

            ApplicationManager.getApplication().assertIsDispatchThread();
            if (ApplicationManager.getApplication().isWriteAccessAllowed()) {
              // makes no sense to start from within write action, will cancel anyway
              // we'll restart when write action finish
              return;
            }
            if (documentManager.hasUncommitedDocuments()) {
              documentManager.cancelAndRunWhenAllCommitted("restart daemon when all committed", this);
              return;
            }

            Map<FileEditor, HighlightingPass[]> passes = new THashMap<FileEditor, HighlightingPass[]>(activeEditors.size());
            for (FileEditor fileEditor : activeEditors) {
              BackgroundEditorHighlighter highlighter = fileEditor.getBackgroundHighlighter();
              if (highlighter != null) {
                HighlightingPass[] highlightingPasses = highlighter.createPassesForEditor();
                passes.put(fileEditor, highlightingPasses);
              }
            }
            // cancel all after calling createPasses() since there are perverts {@link com.intellij.util.xml.ui.DomUIFactoryImpl} who are changing PSI there
            cancelUpdateProgress(true, "Cancel by alarm");
            myAlarm.cancelAllRequests();
            DaemonProgressIndicator progress = createUpdateProgress();
            myPassExecutorService.submitPasses(passes, progress, Job.DEFAULT_PRIORITY);
          }
        };


        if (activeEditor == null) {
          runnable.run();
        }
        else {
          documentManager.cancelAndRunWhenAllCommitted("start daemon when all committed", runnable);
        }
      }
    };
  }

  @NotNull
  private synchronized DaemonProgressIndicator createUpdateProgress() {
    DaemonProgressIndicator progress = new DaemonProgressIndicator() {
      @Override
      public void stopIfRunning() {
        super.stopIfRunning();
        myProject.getMessageBus().syncPublisher(DAEMON_EVENT_TOPIC).daemonFinished();
      }
    };
    progress.start();
    myUpdateProgress = progress;
    return progress;
  }

  @Override
  public void autoImportReferenceAtCursor(@NotNull Editor editor, @NotNull PsiFile file) {
    for (ReferenceImporter importer : Extensions.getExtensions(ReferenceImporter.EP_NAME)) {
      if (importer.autoImportReferenceAtCursor(editor, file)) break;
    }
  }

  synchronized DaemonProgressIndicator getUpdateProgress() {
    return myUpdateProgress;
  }

  @TestOnly
  public void allowToInterrupt(boolean can) {
    allowToInterrupt = can;
  }

  @NotNull
  private Collection<FileEditor> getSelectedEditors() {
    // Editors in modal context
    List<Editor> editors = getActiveEditors();

    Collection<FileEditor> activeFileEditors = new THashSet<FileEditor>(editors.size());
    for (Editor editor : editors) {
      TextEditor textEditor = TextEditorProvider.getInstance().getTextEditor(editor);
      activeFileEditors.add(textEditor);
    }
    if (ApplicationManager.getApplication().getCurrentModalityState() != ModalityState.NON_MODAL) {
      return activeFileEditors;
    }

    // Editors in tabs.
    Collection<FileEditor> result = new THashSet<FileEditor>();
    Collection<Document> documents = new THashSet<Document>(activeFileEditors.size());
    final FileEditor[] tabEditors = FileEditorManager.getInstance(myProject).getSelectedEditors();
    for (FileEditor tabEditor : tabEditors) {
      if (tabEditor instanceof TextEditor) {
        documents.add(((TextEditor)tabEditor).getEditor().getDocument());
      }
      result.add(tabEditor);
    }
    // do not duplicate documents
    for (FileEditor fileEditor : activeFileEditors) {
      if (fileEditor instanceof TextEditor && documents.contains(((TextEditor)fileEditor).getEditor().getDocument())) continue;
      result.add(fileEditor);
    }
    return result;
  }

  @NotNull
  private List<Editor> getActiveEditors() {
    return myEditorTracker.getActiveEditors();
  }

}
