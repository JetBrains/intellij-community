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
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzerSettings;
import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.daemon.ReferenceImporter;
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.intention.impl.IntentionHintComponent;
import com.intellij.concurrency.Job;
import com.intellij.ide.PowerSaveMode;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.ex.EditorMarkupModel;
import com.intellij.openapi.editor.ex.MarkupModelEx;
import com.intellij.openapi.editor.ex.RangeHighlighterEx;
import com.intellij.openapi.editor.impl.DocumentMarkupModel;
import com.intellij.openapi.editor.markup.MarkupModel;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.packageDependencies.DependencyValidationManager;
import com.intellij.psi.PsiCompiledElement;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.psi.impl.PsiDocumentManagerImpl;
import com.intellij.psi.search.scope.packageSet.NamedScope;
import com.intellij.psi.search.scope.packageSet.NamedScopeManager;
import com.intellij.psi.search.scope.packageSet.NamedScopesHolder;
import com.intellij.util.Alarm;
import com.intellij.util.CommonProcessors;
import com.intellij.util.Processor;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
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
public class DaemonCodeAnalyzerImpl extends DaemonCodeAnalyzer implements JDOMExternalizable, ProjectComponent {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl");

  private static final Key<List<LineMarkerInfo>> MARKERS_IN_EDITOR_DOCUMENT_KEY = Key.create("MARKERS_IN_EDITOR_DOCUMENT");
  private final Project myProject;
  private final DaemonCodeAnalyzerSettings mySettings;
  private final EditorTracker myEditorTracker;
  private DaemonProgressIndicator myUpdateProgress = new DaemonProgressIndicator(); //guarded by this

  private final Runnable myUpdateRunnable = createUpdateRunnable();

  private final Alarm myAlarm = new Alarm();
  private boolean myUpdateByTimerEnabled = true;
  private final Collection<VirtualFile> myDisabledHintsFiles = new THashSet<VirtualFile>();
  private final Collection<PsiFile> myDisabledHighlightingFiles = new THashSet<PsiFile>();

  private final FileStatusMap myFileStatusMap;
  private DaemonCodeAnalyzerSettings myLastSettings;

  private IntentionHintComponent myLastIntentionHint; //guarded by this
  private volatile boolean myDisposed;     // the only possible transition: false -> true
  private volatile boolean myInitialized;  // the only possible transition: false -> true

  @NonNls private static final String DISABLE_HINTS_TAG = "disable_hints";
  @NonNls private static final String FILE_TAG = "file";
  @NonNls private static final String URL_ATT = "url";
  private DaemonListeners myDaemonListeners;
  private final PassExecutorService myPassExecutorService;
  private int myModificationCount = 0;

  private volatile boolean allowToInterrupt = true;
  private StatusBarUpdater myStatusBarUpdater;

  public DaemonCodeAnalyzerImpl(Project project, DaemonCodeAnalyzerSettings daemonCodeAnalyzerSettings, EditorTracker editorTracker) {
    myProject = project;

    mySettings = daemonCodeAnalyzerSettings;
    myEditorTracker = editorTracker;
    myLastSettings = (DaemonCodeAnalyzerSettings)mySettings.clone();

    myFileStatusMap = new FileStatusMap(myProject);
    myPassExecutorService = new PassExecutorService(myProject) {
      protected void afterApplyInformationToEditor(final TextEditorHighlightingPass pass,
                                                   final FileEditor fileEditor,
                                                   final ProgressIndicator updateProgress) {
        if (fileEditor instanceof TextEditor) {
          log(updateProgress, pass, "Apply ");
          Editor editor = ((TextEditor)fileEditor).getEditor();
          repaintErrorStripeRenderer(editor);
        }
      }

      protected boolean isDisposed() {
        return myDisposed || super.isDisposed();
      }
    };
    Disposer.register(project, myPassExecutorService);
    Disposer.register(project, myFileStatusMap);
  }

  static boolean hasErrors(Project project, Document document) {
    return !processHighlights(document, project, HighlightSeverity.ERROR, 0, document.getTextLength(), CommonProcessors.<HighlightInfo>alwaysFalse());
  }

  @NotNull
  @TestOnly
  public static List<HighlightInfo> getHighlights(Document document, HighlightSeverity minSeverity, Project project) {
    List<HighlightInfo> infos = new ArrayList<HighlightInfo>();
    processHighlights(document, project, minSeverity, 0, document.getTextLength(),
                      new CommonProcessors.CollectProcessor<HighlightInfo>(infos));
    return infos;
  }

  public List<HighlightInfo> runMainPasses(@NotNull PsiFile psiFile,
                                           @NotNull Document document,
                                           @NotNull final ProgressIndicator progress) {
    final List<HighlightInfo> result = new ArrayList<HighlightInfo>();
    final VirtualFile virtualFile = psiFile.getVirtualFile();
    if (virtualFile != null && !virtualFile.getFileType().isBinary()) {

      final List<TextEditorHighlightingPass> passes = TextEditorHighlightingPassRegistrarEx.getInstanceEx(myProject)
        .instantiateMainPasses(psiFile, document);
      
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

  @TestOnly
  public List<HighlightInfo> runPasses(@NotNull PsiFile file,
                                       @NotNull Document document,
                                       @NotNull TextEditor textEditor,
                                       @NotNull int[] toIgnore,
                                       boolean canChangeDocument,
                                       @Nullable Runnable callbackWhileWaiting) {
    assert myInitialized;
    assert !myDisposed;
    Application application = ApplicationManager.getApplication();
    application.assertIsDispatchThread();
    assert !application.isWriteAccessAllowed();

    // pump first so that queued event do not interfere
    UIUtil.dispatchAllInvocationEvents();
    UIUtil.dispatchAllInvocationEvents();

    Project project = file.getProject();
    setUpdateByTimerEnabled(false);
    FileStatusMap fileStatusMap = getFileStatusMap();
    for (int ignoreId : toIgnore) {
      fileStatusMap.markFileUpToDate(file.getProject(),document, ignoreId);
    }
    fileStatusMap.allowDirt(canChangeDocument);

    TextEditorBackgroundHighlighter highlighter = (TextEditorBackgroundHighlighter)textEditor.getBackgroundHighlighter();
    final List<TextEditorHighlightingPass> passes = highlighter.getPasses(toIgnore);
    HighlightingPass[] array = passes.toArray(new HighlightingPass[passes.size()]);

    final DaemonProgressIndicator progress = createUpdateProgress();
    progress.setDebug(true);
    myPassExecutorService.submitPasses(Collections.singletonMap((FileEditor)textEditor, array), progress, Job.DEFAULT_PRIORITY);
    try {
      while (progress.isRunning()) {
        try {
          if (progress.isCanceled() && progress.isRunning()) {
            // write action sneaked in the AWT. restart
            waitForTermination();
            Throwable savedException = PassExecutorService.getSavedException(progress);
            if (savedException != null) throw savedException;
            return runPasses(file, document, textEditor, toIgnore, canChangeDocument, callbackWhileWaiting);
          }
          if (callbackWhileWaiting != null) {
            callbackWhileWaiting.run();
          }
          progress.waitFor(100);
          UIUtil.dispatchAllInvocationEvents();
          Throwable savedException = PassExecutorService.getSavedException(progress);
          if (savedException != null) throw savedException;
        }
        catch (RuntimeException e) {
          throw e;
        }
        catch (Error e) {
          e.printStackTrace();
          throw e;
        }
        catch (Throwable e) {
          e.printStackTrace();
          throw new RuntimeException(e);
        }
      }
      UIUtil.dispatchAllInvocationEvents();
      UIUtil.dispatchAllInvocationEvents();

      return getHighlights(document, null, project);
    }
    finally {
      fileStatusMap.allowDirt(true);
      waitForTermination();
    }
  }

  @TestOnly
  public void prepareForTest() {
    if (!myInitialized) {
      projectOpened();
    }
    setUpdateByTimerEnabled(false);
    waitForTermination();
  }

  @TestOnly
  public void cleanupAfterTest(boolean dispose) {
    if (!myProject.isOpen()) return;
    stopProcess(false);
    if (dispose) {
      projectClosed();
      Disposer.dispose(myStatusBarUpdater);
      myStatusBarUpdater = null;
      Disposer.dispose(myDaemonListeners);
      myDaemonListeners = null;
    }
    setUpdateByTimerEnabled(false);
    waitForTermination();
  }

  private void waitForTermination() {
    myPassExecutorService.cancelAll(true);
  }

  @NotNull
  public String getComponentName() {
    return "DaemonCodeAnalyzer";
  }

  public void initComponent() {
  }

  public void disposeComponent() {
  }

  @Override
  public void projectOpened() {
    assert !myInitialized : "Double Initializing";
    myStatusBarUpdater = new StatusBarUpdater(myProject);
    Disposer.register(myProject, myStatusBarUpdater);

    myDaemonListeners = new DaemonListeners(myProject, this, myEditorTracker);
    Disposer.register(myProject, myDaemonListeners);
    reloadScopes();

    myInitialized = true;
    myDisposed = false;
    myFileStatusMap.markAllFilesDirty();
  }

  public void projectClosed() {
    assert myInitialized : "Disposing not initialized component";
    assert !myDisposed : "Double dispose";

    stopProcess(false);

    myDisposed = true;
    myLastSettings = null;
  }

  void repaintErrorStripeRenderer(Editor editor) {
    if (!myProject.isInitialized()) return;
    final Document document = editor.getDocument();
    final PsiFile psiFile = PsiDocumentManager.getInstance(myProject).getPsiFile(document);
    final EditorMarkupModel markup = (EditorMarkupModel)editor.getMarkupModel();
    markup.setErrorPanelPopupHandler(new DaemonEditorPopup(psiFile));
    markup.setErrorStripTooltipRendererProvider(new DaemonTooltipRendererProvider(myProject));
    markup.setMinMarkHeight(DaemonCodeAnalyzerSettings.getInstance().ERROR_STRIPE_MARK_MIN_HEIGHT);
    TrafficLightRenderer.setOrRefreshErrorStripeRenderer(markup, myProject, document, psiFile);
  }

  private final List<Pair<NamedScope, NamedScopesHolder>> myScopes = ContainerUtil.createEmptyCOWList();
  void reloadScopes() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    List<Pair<NamedScope, NamedScopesHolder>> scopeList = new ArrayList<Pair<NamedScope, NamedScopesHolder>>();
    final DependencyValidationManager dependencyValidationManager = DependencyValidationManager.getInstance(myProject);
    addScopesToList(scopeList, NamedScopeManager.getInstance(myProject));
    addScopesToList(scopeList, dependencyValidationManager);
    myScopes.clear();
    myScopes.addAll(scopeList);
    dependencyValidationManager.reloadRules();
  }

  private static void addScopesToList(final List<Pair<NamedScope, NamedScopesHolder>> scopeList, final NamedScopesHolder holder) {
    NamedScope[] scopes = holder.getScopes();
    for (NamedScope scope : scopes) {
      scopeList.add(Pair.create(scope, holder));
    }
  }

  @NotNull
  public List<Pair<NamedScope, NamedScopesHolder>> getScopeBasedHighlightingCachedScopes() {
    return myScopes;
  }

  public void settingsChanged() {
    DaemonCodeAnalyzerSettings settings = DaemonCodeAnalyzerSettings.getInstance();
    if (settings.isCodeHighlightingChanged(myLastSettings)) {
      restart();
    }
    myLastSettings = (DaemonCodeAnalyzerSettings)settings.clone();
  }

  public void updateVisibleHighlighters(@NotNull Editor editor) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    // no need, will not work anyway
  }

  public void setUpdateByTimerEnabled(boolean value) {
    myUpdateByTimerEnabled = value;
    stopProcess(value);
  }

  public boolean isUpdateByTimerEnabled() {
    return myUpdateByTimerEnabled;
  }

  public void setImportHintsEnabled(@NotNull PsiFile file, boolean value) {
    VirtualFile vFile = file.getVirtualFile();
    if (value) {
      myDisabledHintsFiles.remove(vFile);
      stopProcess(true);
    }
    else {
      myDisabledHintsFiles.add(vFile);
      HintManager.getInstance().hideAllHints();
    }
  }

  public void resetImportHintsEnabledForProject() {
    myDisabledHintsFiles.clear();
  }

  public void setHighlightingEnabled(@NotNull PsiFile file, boolean value) {
    if (value) {
      myDisabledHighlightingFiles.remove(file);
    }
    else {
      myDisabledHighlightingFiles.add(file);
    }
  }

  public boolean isHighlightingAvailable(PsiFile file) {
    if (myDisabledHighlightingFiles.contains(file)) return false;

    if (file == null || !file.isPhysical()) return false;
    if (file instanceof PsiCompiledElement) return false;
    final FileType fileType = file.getFileType();
    if (fileType == StdFileTypes.GUI_DESIGNER_FORM){
      return true;
    }
    // To enable T.O.D.O. highlighting
    return !fileType.isBinary();
  }

  public boolean isImportHintsEnabled(@NotNull PsiFile file) {
    return isAutohintsAvailable(file) && !myDisabledHintsFiles.contains(file.getVirtualFile());
  }

  public boolean isAutohintsAvailable(PsiFile file) {
    return isHighlightingAvailable(file) && !(file instanceof PsiCompiledElement);
  }

  public void restart() {
    myFileStatusMap.markAllFilesDirty();
    stopProcess(true);
  }

  @Override
  public void restart(@NotNull PsiFile file) {
    Document document = PsiDocumentManager.getInstance(myProject).getCachedDocument(file);
    if (document == null) return;
    myFileStatusMap.markFileScopeDirty(document, new TextRange(0, document.getTextLength()), file.getTextLength());
    stopProcess(true);
  }

  public List<TextEditorHighlightingPass> getPassesToShowProgressFor(Document document) {
    List<TextEditorHighlightingPass> allPasses = myPassExecutorService.getAllSubmittedPasses();
    List<TextEditorHighlightingPass> result = new ArrayList<TextEditorHighlightingPass>(allPasses.size());
    for (TextEditorHighlightingPass pass : allPasses) {
      if (pass.getDocument() == document || pass.getDocument() == null) {
        result.add(pass);
      }
    }
    return result;
  }

  public boolean isAllAnalysisFinished(@NotNull PsiFile file) {
    if (myDisposed) return false;
    Document document = PsiDocumentManager.getInstance(myProject).getCachedDocument(file);
    return document != null &&
           document.getModificationStamp() == file.getModificationStamp() &&
           myFileStatusMap.allDirtyScopesAreNull(document);
  }

  public boolean isErrorAnalyzingFinished(PsiFile file) {
    if (myDisposed) return false;
    Document document = PsiDocumentManager.getInstance(myProject).getCachedDocument(file);
    return document != null &&
           document.getModificationStamp() == file.getModificationStamp() &&
           myFileStatusMap.getFileDirtyScope(document, Pass.UPDATE_ALL) == null;
  }

  public FileStatusMap getFileStatusMap() {
    return myFileStatusMap;
  }

  public synchronized int getModificationCount() {
    return myModificationCount;
  }
  
  public synchronized boolean isRunning() {
    return myUpdateProgress != null && !myUpdateProgress.isCanceled();
  }

  synchronized void stopProcess(boolean toRestartAlarm) {
    if (!allowToInterrupt) throw new RuntimeException("Cannot interrupt daemon");

    cancelUpdateProgress(toRestartAlarm, "by Stop process");
    myAlarm.cancelAllRequests();
    boolean restart = toRestartAlarm && !myDisposed && myInitialized;
    if (restart) {
      myAlarm.addRequest(myUpdateRunnable, mySettings.AUTOREPARSE_DELAY);
    }
  }

  private synchronized void cancelUpdateProgress(final boolean start, @NonNls String reason) {
    PassExecutorService.log(myUpdateProgress, null, reason, start);
    myModificationCount++;

    if (myUpdateProgress != null) {
      myUpdateProgress.cancel();
      myPassExecutorService.cancelAll(false);
      myUpdateProgress = null;
    }
  }

  public static boolean processHighlights(@NotNull Document document,
                                          @NotNull Project project,
                                          @Nullable("null means all") final HighlightSeverity minSeverity,
                                          final int startOffset,
                                          final int endOffset,
                                          @NotNull final Processor<HighlightInfo> processor) {
    LOG.assertTrue(ApplicationManager.getApplication().isReadAccessAllowed());

    final SeverityRegistrar severityRegistrar = SeverityRegistrar.getInstance(project);
    MarkupModelEx model = (MarkupModelEx)DocumentMarkupModel.forDocument(document, project, true);
    return model.processRangeHighlightersOverlappingWith(startOffset, endOffset, new Processor<RangeHighlighterEx>() {
      public boolean process(RangeHighlighterEx marker) {
        Object tt = marker.getErrorStripeTooltip();
        if (!(tt instanceof HighlightInfo)) return true;
        HighlightInfo info = (HighlightInfo)tt;
        return minSeverity != null && severityRegistrar.compare(info.getSeverity(), minSeverity) < 0
               || info.highlighter == null
               || processor.process(info);
      }
    });
  }

  public static boolean processHighlightsOverlappingOutside(@NotNull Document document,
                                                            @NotNull Project project,
                                                            @Nullable("null means all") final HighlightSeverity minSeverity,
                                                            final int startOffset,
                                                            final int endOffset,
                                                            @NotNull final Processor<HighlightInfo> processor) {
    LOG.assertTrue(ApplicationManager.getApplication().isReadAccessAllowed());

    final SeverityRegistrar severityRegistrar = SeverityRegistrar.getInstance(project);
    MarkupModelEx model = (MarkupModelEx)DocumentMarkupModel.forDocument(document, project, true);
    return model.processRangeHighlightersOutside(startOffset, endOffset, new Processor<RangeHighlighterEx>() {
      public boolean process(RangeHighlighterEx marker) {
        Object tt = marker.getErrorStripeTooltip();
        if (!(tt instanceof HighlightInfo)) return true;
        HighlightInfo info = (HighlightInfo)tt;
        return minSeverity != null && severityRegistrar.compare(info.getSeverity(), minSeverity) < 0
               || info.highlighter == null
               || processor.process(info);
      }
    });
  }


  public static boolean processHighlightsNearOffset(@NotNull Document document,
                                                    @NotNull Project project,
                                                    @NotNull final HighlightSeverity minSeverity,
                                                    final int offset,
                                                    final boolean includeFixRange,
                                                    @NotNull final Processor<HighlightInfo> processor) {
    return processHighlights(document, project, null, 0, document.getTextLength(), new Processor<HighlightInfo>() {
      public boolean process(HighlightInfo info) {
        if (!isOffsetInsideHighlightInfo(offset, info, includeFixRange)) return true;

        int compare = info.getSeverity().compareTo(minSeverity);
        return compare < 0 || processor.process(info);
      }
    });
  }

  @Nullable
  public HighlightInfo findHighlightByOffset(Document document, final int offset, final boolean includeFixRange) {
    final List<HighlightInfo> foundInfoList = new SmartList<HighlightInfo>();
    processHighlightsNearOffset(document, myProject, HighlightSeverity.INFORMATION, offset, includeFixRange, new Processor<HighlightInfo>() {
      public boolean process(HighlightInfo info) {
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

  private static boolean isOffsetInsideHighlightInfo(int offset, HighlightInfo info, boolean includeFixRange) {
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
  public static List<LineMarkerInfo> getLineMarkers(Document document, Project project) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    MarkupModel markup = DocumentMarkupModel.forDocument(document, project, true);
    return markup.getUserData(MARKERS_IN_EDITOR_DOCUMENT_KEY);
  }

  public static void setLineMarkers(@NotNull Document document, List<LineMarkerInfo> lineMarkers, Project project) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    MarkupModel markup = DocumentMarkupModel.forDocument(document, project, true);
    markup.putUserData(MARKERS_IN_EDITOR_DOCUMENT_KEY, lineMarkers);
  }

  public synchronized void setLastIntentionHint(Project project, PsiFile file, Editor editor, ShowIntentionsPass.IntentionsInfo intentions, boolean hasToRecreate) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    hideLastIntentionHint();
    IntentionHintComponent hintComponent = IntentionHintComponent.showIntentionHint(project, file, editor, intentions, false);
    if (hasToRecreate) {
      hintComponent.recreate();
    }
    myLastIntentionHint = hintComponent;
  }

  public synchronized void hideLastIntentionHint() {
    if (myLastIntentionHint != null && myLastIntentionHint.isVisible()) {
      myLastIntentionHint.hide();
      myLastIntentionHint = null;
    }
  }

  public synchronized IntentionHintComponent getLastIntentionHint() {
    return myLastIntentionHint;
  }

  public void writeExternal(Element parentNode) throws WriteExternalException {
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

  public void readExternal(Element parentNode) throws InvalidDataException {
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

  private Runnable createUpdateRunnable() {
    return new Runnable() {
      public void run() {
        if (myDisposed || !myProject.isInitialized()) return;
        if (PowerSaveMode.isEnabled()) return;
        Editor activeEditor = FileEditorManager.getInstance(myProject).getSelectedTextEditor();

        Runnable runnable = new Runnable() {
          public void run() {
            PassExecutorService.log(myUpdateProgress, null, "Update Runnable. myUpdateByTimerEnabled:",myUpdateByTimerEnabled," something disposed:",PowerSaveMode.isEnabled() || myDisposed || !myProject.isInitialized()," activeEditors:",myProject.isDisposed() ? null : myDaemonListeners.getSelectedEditors());
            if (!myUpdateByTimerEnabled) return;
            if (myDisposed) return;
            ApplicationManager.getApplication().assertIsDispatchThread();

            final Collection<FileEditor> activeEditors = myDaemonListeners.getSelectedEditors();
            if (activeEditors.isEmpty()) return;

            ApplicationManager.getApplication().assertIsDispatchThread();
            if (ApplicationManager.getApplication().isWriteAccessAllowed()) {
              // makes no sense to start from within write action, will cancel anyway
              // we'll restart when write action finish
              return;
            }
            PsiDocumentManager documentManager = PsiDocumentManager.getInstance(myProject);
            if (documentManager.hasUncommitedDocuments()) {
              if (ModalityState.current() == ModalityState.NON_MODAL) {
                ((PsiDocumentManagerImpl)documentManager).cancelAndRunWhenAllCommitted("restart daemon when all committed", this);
                return;
              }
              else {
                // when modal dialog is open, DocumentCommitThread is not able to commit in background, so force commit here
                documentManager.commitAllDocuments();
              }
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
          ((PsiDocumentManagerImpl)PsiDocumentManager.getInstance(myProject)).cancelAndRunWhenAllCommitted(
            "start daemon when all committed", runnable);
        }
      }
    };
  }

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

  public boolean canChangeFileSilently(PsiFileSystemItem file) {
    return myDaemonListeners.canChangeFileSilently(file);
  }

  public void autoImportReferenceAtCursor(@NotNull Editor editor, @NotNull PsiFile file) {
    for(ReferenceImporter importer: Extensions.getExtensions(ReferenceImporter.EP_NAME)) {
      if (importer.autoImportReferenceAtCursor(editor, file)) break;
    }
  }

  @NotNull
  @TestOnly
  public static List<HighlightInfo> getFileLevelHighlights(Project project,PsiFile file ) {
    return UpdateHighlightersUtil.getFileLeveleHighlights(project, file);
  }

  @TestOnly
  public void allowToInterrupt(boolean can) {
    allowToInterrupt = can;
  }

  @TestOnly
  public synchronized DaemonProgressIndicator getUpdateProgress() {
    return myUpdateProgress;
  }
}
