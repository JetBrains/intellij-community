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
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.editor.ex.EditorMarkupModel;
import com.intellij.openapi.editor.ex.MarkupModelEx;
import com.intellij.openapi.editor.ex.RangeHighlighterEx;
import com.intellij.openapi.editor.markup.MarkupModel;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.packageDependencies.DependencyValidationManager;
import com.intellij.psi.PsiCompiledElement;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileSystemItem;
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

import javax.swing.*;
import java.util.*;

/**
 * This class also controls the auto-reparse and auto-hints.
 */
public class DaemonCodeAnalyzerImpl extends DaemonCodeAnalyzer implements JDOMExternalizable {
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
  private volatile boolean myDisposed;
  private boolean myInitialized;

  @NonNls private static final String DISABLE_HINTS_TAG = "disable_hints";
  @NonNls private static final String FILE_TAG = "file";
  @NonNls private static final String URL_ATT = "url";
  private DaemonListeners myDaemonListeners;
  private StatusBarUpdater myStatusBarUpdater;
  private final PassExecutorService myPassExecutorService;
  private int myModificationCount = 0;

  //   @TestOnly
  private boolean allowToInterrupt = true;

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
    processHighlights(document, project, minSeverity, 0, document.getTextLength(), new CommonProcessors.CollectProcessor<HighlightInfo>(infos));
    return infos;
  }

  public List<HighlightInfo> runMainPasses(@NotNull PsiFile psiFile,
                                           @NotNull Document document,
                                           @NotNull final ProgressIndicator progress) {
    GeneralHighlightingPass action1 = new GeneralHighlightingPass(myProject, psiFile, document, 0, psiFile.getTextLength(), true);
    action1.doCollectInformation(progress);

    List<HighlightInfo> result = new ArrayList<HighlightInfo>();
    result.addAll(action1.getHighlights());

    LocalInspectionsPass action3 = new LocalInspectionsPass(psiFile, document, 0, psiFile.getTextLength());
    action3.doCollectInformation(progress);

    result.addAll(action3.getHighlights());

    return result;
  }

  @TestOnly
  public List<HighlightInfo> runPasses(@NotNull PsiFile file,
                                       @NotNull Document document,
                                       @NotNull TextEditor textEditor,
                                       @NotNull final ProgressIndicator progress,
                                       @NotNull int[] toIgnore,
                                       boolean allowDirt,
                                       final boolean apply) {
    // pump first so that queued event do not interfere
    if (SwingUtilities.isEventDispatchThread()) {
      UIUtil.dispatchAllInvocationEvents();
    }
    else {
      UIUtil.pump();
    }

    Project project = file.getProject();
    setUpdateByTimerEnabled(false);
    FileStatusMap fileStatusMap = getFileStatusMap();
    for (int ignoreId : toIgnore) {
      fileStatusMap.markFileUpToDate(document, file, ignoreId);
    }
    fileStatusMap.allowDirt(allowDirt);
    try {
      TextEditorBackgroundHighlighter highlighter = (TextEditorBackgroundHighlighter)textEditor.getBackgroundHighlighter();
      final List<TextEditorHighlightingPass> passes = highlighter.getPasses(toIgnore);
      ProgressManager.getInstance().runProcess(new Runnable() {
        public void run() {
          for (TextEditorHighlightingPass pass : passes) {
            pass.collectInformation(progress);
            if (apply) {
              // apply incremental highlights scheduled for AWT thread
              if (SwingUtilities.isEventDispatchThread()) {
                UIUtil.dispatchAllInvocationEvents();
              }
              else {
                UIUtil.pump();
              }
              pass.applyInformationToEditor();
            }
          }
        }
      }, progress);

      return getHighlights(document, null, project);
    }
    finally {
      fileStatusMap.allowDirt(true);
      myPassExecutorService.cancelAll(true);
    }
  }

  @NotNull
  public String getComponentName() {
    return "DaemonCodeAnalyzer";
  }

  public void initComponent() {
  }

  public void disposeComponent() {
  }

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

    // clear dangling references to PsiFiles/Documents. SCR#10358
    myFileStatusMap.markAllFilesDirty();

    stopProcess(false);

    myDisposed = true;
    myLastSettings = null;
    myInitialized = false;
  }

  @TestOnly
  public boolean isInitialized() {
    return myInitialized;
  }

  void repaintErrorStripeRenderer(Editor editor) {
    if (!myProject.isInitialized()) return;
    final Document document = editor.getDocument();
    final PsiFile psiFile = PsiDocumentManager.getInstance(myProject).getPsiFile(document);
    final EditorMarkupModel markup = (EditorMarkupModel)editor.getMarkupModel();
    markup.setErrorStripeRenderer(new TrafficLightRenderer(myProject, this, document, psiFile));
    markup.setErrorPanelPopupHandler(new DaemonEditorPopup(psiFile));
    markup.setErrorStripTooltipRendererProvider(new DaemonTooltipRendererProvider(myProject));
    markup.setMinMarkHeight(DaemonCodeAnalyzerSettings.getInstance().ERROR_STRIPE_MARK_MIN_HEIGHT);
  }

  private final List<Pair<NamedScope, NamedScopesHolder>> myScopes = ContainerUtil.createEmptyCOWList();
  void reloadScopes() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    List<Pair<NamedScope, NamedScopesHolder>> scopeList = new ArrayList<Pair<NamedScope, NamedScopesHolder>>();
    addScopesToList(scopeList, NamedScopeManager.getInstance(myProject));
    addScopesToList(scopeList, DependencyValidationManager.getInstance(myProject));
    myScopes.clear();
    myScopes.addAll(scopeList);
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
    stopProcess(true);
  }

  public boolean isUpdateByTimerEnabled() {
    return myUpdateByTimerEnabled;
  }

  public void setImportHintsEnabled(PsiFile file, boolean value) {
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

  public void setHighlightingEnabled(PsiFile file, boolean value) {
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

  public boolean isImportHintsEnabled(PsiFile file) {
    return isAutohintsAvailable(file) && !myDisabledHintsFiles.contains(file.getVirtualFile());
  }

  public boolean isAutohintsAvailable(PsiFile file) {
    return isHighlightingAvailable(file) && !(file instanceof PsiCompiledElement);
  }

  public void restart() {
    myFileStatusMap.markAllFilesDirty();
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

  public synchronized void stopProcess(boolean toRestartAlarm) {
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
    MarkupModelEx model = (MarkupModelEx)((DocumentEx)document).getMarkupModel(project);
    return model.processHighlightsOverlappingWith(startOffset, endOffset, new Processor<RangeHighlighterEx>() {
      public boolean process(RangeHighlighterEx marker) {
        Object tt = marker.getErrorStripeTooltip();
        if (!(tt instanceof HighlightInfo)) return true;
        HighlightInfo info = (HighlightInfo)tt;
        return minSeverity != null && severityRegistrar.compare(info.getSeverity(), minSeverity) < 0 || processor.process(info);
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
        if (compare < 0) {
          return true;
        }

        return processor.process(info);
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
    if (startOffset > offset || offset > endOffset) {
      if (!includeFixRange) return false;
      if (info.fixMarker == null || !info.fixMarker.isValid()) return false;
      startOffset = info.fixMarker.getStartOffset();
      endOffset = info.fixMarker.getEndOffset();
      if (startOffset > offset || offset > endOffset) return false;
    }
    return true;
  }

  static void addHighlight(MarkupModel markup,
                           Project project,
                           HighlightInfo toAdd) {
    ApplicationManager.getApplication().assertIsDispatchThread();

    stripWarningsCoveredByErrors(project, markup, toAdd);
    
    //DaemonCodeAnalyzer codeAnalyzer = DaemonCodeAnalyzer.getInstance(project);
    //if (codeAnalyzer instanceof DaemonCodeAnalyzerImpl && ((DaemonCodeAnalyzerImpl)codeAnalyzer).myStatusBarUpdater != null) {
    //  ((DaemonCodeAnalyzerImpl)codeAnalyzer).myStatusBarUpdater.updateStatus();
    //}
    
  }

  private static void stripWarningsCoveredByErrors(Project project, MarkupModel markup, final HighlightInfo toAdd) {
    final SeverityRegistrar severityRegistrar = SeverityRegistrar.getInstance(project);
    final Set<HighlightInfo> covered = new THashSet<HighlightInfo>();

    // either toAdd is warning and covered by one of errors in highlightsToSet or toAdd is an error and covers warnings in highlightsToSet or it is OK
    final boolean addingError = severityRegistrar.compare(HighlightSeverity.ERROR, toAdd.getSeverity()) <= 0;
    boolean toAddIsVisible = processHighlights(markup.getDocument(), project, null, toAdd.getActualStartOffset(),
                                               toAdd.getActualEndOffset(), new Processor<HighlightInfo>() {
        public boolean process(HighlightInfo interval) {
          boolean isError = severityRegistrar.compare(HighlightSeverity.ERROR, interval.getSeverity()) <= 0;
          if (addingError && !isError && isCoveredBy(interval, toAdd)) {
            covered.add(interval);
          }
          return addingError || !isError || !isCoveredBy(toAdd, interval);
        }
      });
    if (toAddIsVisible) {
      // ok
      //highlightsToSet.add(toAdd);
    }
    else {
      // toAdd is covered by
      markup.removeHighlighter(toAdd.highlighter);
    }
    for (HighlightInfo warning : covered) {
      RangeHighlighter highlighter = warning.highlighter;
      if (highlighter != null) {
        markup.removeHighlighter(highlighter);
      }
    }
  }

  static boolean isCoveredBy(HighlightInfo info, HighlightInfo coveredBy) {
    return coveredBy.startOffset <= info.startOffset && info.endOffset <= coveredBy.endOffset && info.getGutterIconRenderer() == null;
  }

  @Nullable
  public static List<LineMarkerInfo> getLineMarkers(Document document, Project project) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    MarkupModel markup = document.getMarkupModel(project);
    return markup.getUserData(MARKERS_IN_EDITOR_DOCUMENT_KEY);
  }

  public static void setLineMarkers(@NotNull Document document, List<LineMarkerInfo> lineMarkers, Project project) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    MarkupModel markup = document.getMarkupModel(project);
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
        if (!myUpdateByTimerEnabled) return;
        if (PowerSaveMode.isEnabled()) return;
        if (myDisposed || !myProject.isInitialized()) return;
        final Collection<FileEditor> activeEditors = myDaemonListeners.getSelectedEditors();
        if (activeEditors.isEmpty()) return;
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
        DaemonProgressIndicator progress;
        synchronized (DaemonCodeAnalyzerImpl.this) {
          DaemonProgressIndicator indicator = new DaemonProgressIndicator();
          indicator.start();
          myUpdateProgress = progress = indicator;
        }
        myPassExecutorService.submitPasses(passes, progress, Job.DEFAULT_PRIORITY);
      }
    };
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
}
