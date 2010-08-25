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
import com.intellij.injected.editor.EditorWindow;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.EditorMarkupModel;
import com.intellij.openapi.editor.markup.MarkupModel;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider;
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
import com.intellij.psi.search.scope.packageSet.NamedScope;
import com.intellij.psi.search.scope.packageSet.NamedScopeManager;
import com.intellij.psi.search.scope.packageSet.NamedScopesHolder;
import com.intellij.util.Alarm;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
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
public class DaemonCodeAnalyzerImpl extends DaemonCodeAnalyzer implements JDOMExternalizable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl");

  private static final Key<List<HighlightInfo>> HIGHLIGHTS_IN_EDITOR_DOCUMENT_KEY = Key.create("HIGHLIGHTS_IN_EDITOR_DOCUMENT");
  private static final Key<List<LineMarkerInfo>> MARKERS_IN_EDITOR_DOCUMENT_KEY = Key.create("MARKERS_IN_EDITOR_DOCUMENT");

  private final Project myProject;
  private final DaemonCodeAnalyzerSettings mySettings;
  private final EditorTracker myEditorTracker;
  private DaemonProgressIndicator myUpdateProgress = new DaemonProgressIndicator(); //guarded by this
  private DaemonProgressIndicator myUpdateVisibleProgress = new DaemonProgressIndicator(); //guarded by this

  private final Runnable myUpdateRunnable = createUpdateRunnable();

  private final Alarm myAlarm = new Alarm();

  private boolean myUpdateByTimerEnabled = true;
  private final Collection<VirtualFile> myDisabledHintsFiles = new THashSet<VirtualFile>();
  private final Collection<PsiFile> myDisabledHighlightingFiles = new THashSet<PsiFile>();
  private final FileStatusMap myFileStatusMap;

  private DaemonCodeAnalyzerSettings myLastSettings;
  private IntentionHintComponent myLastIntentionHint; //guarded by this

  private boolean myDisposed;
  private boolean myInitialized;
  @NonNls private static final String DISABLE_HINTS_TAG = "disable_hints";

  @NonNls private static final String FILE_TAG = "file";
  @NonNls private static final String URL_ATT = "url";
  private DaemonListeners myDaemonListeners;
  private StatusBarUpdater myStatusBarUpdater;
  private final PassExecutorService myPassExecutorService;
  private static final Key<List<HighlightInfo>> HIGHLIGHTS_TO_REMOVE_KEY = Key.create("HIGHLIGHTS_TO_REMOVE");
  private int myModificationCount = 0;

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
    if (!myUpdateByTimerEnabled) return;
    if (editor instanceof EditorWindow) editor = ((EditorWindow)editor).getDelegate();

    final TextEditor textEditor = TextEditorProvider.getInstance().getTextEditor(editor);
    BackgroundEditorHighlighter highlighter = textEditor.getBackgroundHighlighter();
    if (highlighter == null) return;
    final HighlightingPass[] highlightingPasses = highlighter.createPassesForVisibleArea();

    DaemonProgressIndicator progress;
    synchronized (this) {
      recreateVisibleProgress();

      progress = myUpdateVisibleProgress;
    }
    myPassExecutorService.renewVisiblePasses(textEditor, highlightingPasses, progress);
  }

  private synchronized void cancelVisibleProgress() {
    if (myUpdateVisibleProgress != null) {
      myUpdateVisibleProgress.cancel();
      myUpdateVisibleProgress = null;
    }
  }

  private synchronized void recreateVisibleProgress() {
    if (myUpdateVisibleProgress == null) {
      myUpdateVisibleProgress = new DaemonProgressIndicator();
      myUpdateVisibleProgress.start();
    }
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

  public List<TextEditorHighlightingPass> getPassesToShowProgressFor(PsiFile file) {
    Document document = PsiDocumentManager.getInstance(myProject).getDocument(file);
    List<TextEditorHighlightingPass> allPasses = myPassExecutorService.getAllSubmittedPasses();
    List<TextEditorHighlightingPass> result = new ArrayList<TextEditorHighlightingPass>(allPasses.size());
    for (TextEditorHighlightingPass pass : allPasses) {
      if (pass.getDocument() == document || pass.getDocument() == null) {
        result.add(pass);
      }
    }
    return result;
  }

  public boolean isAllAnalysisFinished(PsiFile file) {
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
      cancelVisibleProgress();
      myUpdateProgress = null;
    }
  }

  private static DaemonProgressIndicator recreateProgress() {
    DaemonProgressIndicator myUpdateProgress = new DaemonProgressIndicator();
    myUpdateProgress.start();
    return myUpdateProgress;
  }

  @Nullable
  public static List<HighlightInfo> getHighlights(Document document, Project project) {
    LOG.assertTrue(ApplicationManager.getApplication().isReadAccessAllowed());
    MarkupModel markup = document.getMarkupModel(project);
    return getHighlights(markup);
  }

  static List<HighlightInfo> getHighlights(MarkupModel markup) {
    return markup.getUserData(HIGHLIGHTS_IN_EDITOR_DOCUMENT_KEY);
  }

  @NotNull
  public static List<HighlightInfo> getHighlights(Document document, HighlightSeverity minSeverity, Project project) {
    return getHighlights(document, minSeverity, project, Integer.MIN_VALUE, Integer.MAX_VALUE);
  }

  @NotNull
  public static List<HighlightInfo> getHighlights(Document document, HighlightSeverity minSeverity, Project project, int startOffset, int endOffset) {
    LOG.assertTrue(ApplicationManager.getApplication().isReadAccessAllowed());
    List<HighlightInfo> highlights = getHighlights(document, project);
    if (highlights == null) return Collections.emptyList();
    List<HighlightInfo> array = new ArrayList<HighlightInfo>();
    final SeverityRegistrar instance = SeverityRegistrar.getInstance(project);

    for (HighlightInfo info : highlights) {
      if (instance.compare(info.getSeverity(), minSeverity) >= 0 &&
          info.startOffset >= startOffset &&
          info.endOffset <= endOffset) {
        array.add(info);
      }
    }
    return array;
  }

  @NotNull
  public static List<HighlightInfo> getHighlightsAround(Document document, Project project, int offset) {
    LOG.assertTrue(ApplicationManager.getApplication().isReadAccessAllowed());
    List<HighlightInfo> highlights = getHighlights(document, project);
    if (highlights == null) return Collections.emptyList();
    List<HighlightInfo> array = new ArrayList<HighlightInfo>();

    for (HighlightInfo info : highlights) {
      if (isOffsetInsideHighlightInfo(offset, info, true)) {
        array.add(info);
      }
    }
    return array;
  }

  @Nullable
  public HighlightInfo findHighlightByOffset(Document document, int offset, boolean includeFixRange) {
    List<HighlightInfo> highlights = getHighlights(document, myProject);
    if (highlights == null) return null;

    List<HighlightInfo> foundInfoList = new SmartList<HighlightInfo>();
    for (HighlightInfo info : highlights) {
      if (!isOffsetInsideHighlightInfo(offset, info, includeFixRange)) continue;

      if (!foundInfoList.isEmpty()) {
        HighlightInfo foundInfo = foundInfoList.get(0);
        int compare = foundInfo.getSeverity().compareTo(info.getSeverity());
        if (compare < 0) {
          foundInfoList.clear();
        }
        else if (compare > 0) {
          continue;
        }
      }
      foundInfoList.add(info);
    }

    if (foundInfoList.isEmpty()) return null;
    if (foundInfoList.size() == 1) return foundInfoList.get(0);
    return new HighlightInfoComposite(foundInfoList);
  }

  private static boolean isOffsetInsideHighlightInfo(int offset, HighlightInfo info, boolean includeFixRange) {
    if (info.highlighter == null || !info.highlighter.isValid()) return false;
    int startOffset = info.highlighter.getStartOffset();
    int endOffset = info.highlighter.getEndOffset();
    if (startOffset > offset || offset > endOffset) {
      if (!includeFixRange) return false;
      if (info.fixMarker == null || !info.fixMarker.isValid()) return false;
      startOffset = info.fixMarker.getStartOffset();
      endOffset = info.fixMarker.getEndOffset();
      if (startOffset > offset || offset > endOffset) return false;
    }
    return true;
  }

  static void setHighlights(MarkupModel markup, Project project, List<HighlightInfo> highlightsToSet, List<HighlightInfo> highlightsToRemove) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    stripWarningsCoveredByErrors(project, highlightsToSet, markup);
    markup.putUserData(HIGHLIGHTS_IN_EDITOR_DOCUMENT_KEY, Collections.unmodifiableList(highlightsToSet));

    markup.putUserData(HIGHLIGHTS_TO_REMOVE_KEY, Collections.unmodifiableList(highlightsToRemove));

    DaemonCodeAnalyzer codeAnalyzer = DaemonCodeAnalyzer.getInstance(project);
    if (codeAnalyzer instanceof DaemonCodeAnalyzerImpl && ((DaemonCodeAnalyzerImpl)codeAnalyzer).myStatusBarUpdater != null) {
      ((DaemonCodeAnalyzerImpl)codeAnalyzer).myStatusBarUpdater.updateStatus();
    }
  }

  private static void stripWarningsCoveredByErrors(final Project project, List<HighlightInfo> highlights, MarkupModel markup) {
    final SeverityRegistrar severityRegistrar = SeverityRegistrar.getInstance(project);
    Collection<HighlightInfo> errors = new ArrayList<HighlightInfo>();
    for (HighlightInfo highlight : highlights) {
      if (severityRegistrar.compare(highlight.getSeverity(), HighlightSeverity.ERROR) >= 0) {
        errors.add(highlight);
      }
    }

    for (Iterator<HighlightInfo> it = highlights.iterator(); it.hasNext();) {
      HighlightInfo highlight = it.next();
      if (severityRegistrar.compare(HighlightSeverity.ERROR, highlight.getSeverity()) > 0 && highlight.getSeverity().myVal > 0) {
        for (HighlightInfo errorInfo : errors) {
          if (isCoveredBy(highlight, errorInfo)) {
            it.remove();
            RangeHighlighter highlighter = highlight.highlighter;
            if (highlighter != null) {
              markup.removeHighlighter(highlighter);
            }
            break;
          }
        }
      }
    }
  }

  private static boolean isCoveredBy(HighlightInfo info, HighlightInfo coveredBy) {
    return info.startOffset >= coveredBy.startOffset && info.endOffset <= coveredBy.endOffset && info.getGutterIconRenderer() == null;
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
          myUpdateProgress = progress = recreateProgress();
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
  static List<HighlightInfo> getHighlightsToRemove(MarkupModel markup) {
    List<HighlightInfo> infos = markup.getUserData(HIGHLIGHTS_TO_REMOVE_KEY);
    return infos == null ? Collections.<HighlightInfo>emptyList() : infos;
  }

  @NotNull
  @TestOnly
  public static List<HighlightInfo> getFileLevelHighlights(Project project,PsiFile file ) {
    return UpdateHighlightersUtil.getFileLeveleHighlights(project, file);
  }

  @TestOnly
  public void clearPasses() {
    myPassExecutorService.cancelAll(true);
  }
}
