package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeHighlighting.BackgroundEditorHighlighter;
import com.intellij.codeHighlighting.HighlightingPass;
import com.intellij.codeHighlighting.Pass;
import com.intellij.codeHighlighting.TextEditorHighlightingPass;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzerSettings;
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.intention.impl.IntentionHintComponent;
import com.intellij.concurrency.Job;
import com.intellij.ide.highlighter.custom.impl.CustomFileType;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.EditorMarkupModel;
import com.intellij.openapi.editor.markup.MarkupModel;
import com.intellij.openapi.editor.markup.RangeHighlighter;
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
import com.intellij.psi.PsiCompiledElement;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiPlainTextFile;
import com.intellij.psi.search.scope.packageSet.NamedScope;
import com.intellij.psi.search.scope.packageSet.NamedScopesHolder;
import com.intellij.util.Alarm;
import com.intellij.util.SmartList;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * This class also controls the auto-reparse and auto-hints.
 */
public class DaemonCodeAnalyzerImpl extends DaemonCodeAnalyzer implements JDOMExternalizable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl");

  private static final Key<HighlightInfo[]> HIGHLIGHTS_IN_EDITOR_DOCUMENT_KEY = Key.create("DaemonCodeAnalyzerImpl.HIGHLIGHTS_IN_EDITOR_DOCUMENT_KEY");
  private static final Key<LineMarkerInfo[]> MARKERS_IN_EDITOR_DOCUMENT_KEY = Key.create("DaemonCodeAnalyzerImpl.MARKERS_IN_EDITOR_DOCUMENT_KEY");

  private final Project myProject;
  private final DaemonCodeAnalyzerSettings mySettings;
  private final EditorTracker myEditorTracker;
  private DaemonProgressIndicator myUpdateProgress = new DaemonProgressIndicator();
  private DaemonProgressIndicator myUpdateVisibleProgress = new DaemonProgressIndicator();

  private final Runnable myUpdateRunnable = createUpdateRunnable();

  private final Alarm myAlarm = new Alarm();

  private boolean myUpdateByTimerEnabled = true;
  private final Set<VirtualFile> myDisabledHintsFiles = new THashSet<VirtualFile>();
  private final Set<PsiFile> myDisabledHighlightingFiles = new THashSet<PsiFile>();
  private final FileStatusMap myFileStatusMap;

  private DaemonCodeAnalyzerSettings myLastSettings;
  private IntentionHintComponent myLastIntentionHint;

  private boolean myDisposed;
  private boolean myInitialized;
  @NonNls private static final String DISABLE_HINTS_TAG = "disable_hints";

  @NonNls private static final String FILE_TAG = "file";
  @NonNls private static final String URL_ATT = "url";
  private DaemonListeners myDaemonListeners;
  private StatusBarUpdater myStatusBarUpdater;
  private final PassExecutorService myPassExecutorService;

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
  }

  @NotNull
  public String getComponentName() {
    return "DaemonCodeAnalyzer";
  }

  public void initComponent() {
    myFileStatusMap.markAllFilesDirty();
  }

  public void disposeComponent() {
    myFileStatusMap.markAllFilesDirty();
  }

  public void projectOpened() {
    myStatusBarUpdater = new StatusBarUpdater(myProject);

    myDaemonListeners = new DaemonListeners(myProject,this, myEditorTracker);
    reloadScopes();

    myInitialized = true;
    myFileStatusMap.markAllFilesDirty();
  }
  public void projectClosed() {
    myFileStatusMap.markAllFilesDirty();
    dispose();
  }

  void repaintErrorStripeRenderer(Editor editor) {
    if (myProject.isDisposed()) return;
    final Document document = editor.getDocument();
    final PsiFile psiFile = PsiDocumentManager.getInstance(myProject).getPsiFile(document);
    ((EditorMarkupModel)editor.getMarkupModel()).setErrorStripeRenderer(new TrafficLightRenderer(myProject, this, document, psiFile));
  }

  private List<Pair<NamedScope, NamedScopesHolder>> myScopes = Collections.emptyList();
  void reloadScopes() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    List<Pair<NamedScope, NamedScopesHolder>> scopeList = new ArrayList<Pair<NamedScope, NamedScopesHolder>>();
    final NamedScopesHolder[] holders = myProject.getComponents(NamedScopesHolder.class);
    for (NamedScopesHolder holder : holders) {
      NamedScope[] scopes = holder.getScopes();
      for (NamedScope scope : scopes) {
        scopeList.add(Pair.create(scope, holder));
      }
    }
    myScopes = scopeList;
  }

  @NotNull
  public List<Pair<NamedScope, NamedScopesHolder>> getScopeBasedHighlightingCachedScopes() {
    return myScopes;
  }

  private void dispose() {
    if (myDisposed) return;
    if (myInitialized) {
      myDaemonListeners.dispose();
      stopProcess(false);
      myPassExecutorService.dispose();
      myStatusBarUpdater.dispose();
    }

    // clear dangling references to PsiFiles/Documents. SCR#10358
    myFileStatusMap.markAllFilesDirty();

    myDisposed = true;

    myLastSettings = null;
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
    if (ApplicationManager.getApplication().isUnitTestMode()) return;

    final TextEditor textEditor = TextEditorProvider.getInstance().getTextEditor(editor);
    BackgroundEditorHighlighter highlighter = textEditor.getBackgroundHighlighter();
    if (highlighter == null) return;
    final HighlightingPass[] highlightingPasses = highlighter.createPassesForVisibleArea();
    //setLastIntentionHint(null);

    myPassExecutorService.renewVisiblePasses(textEditor, highlightingPasses, myUpdateVisibleProgress);
  }

  private void renewUpdateVisibleProgress() {
    myUpdateVisibleProgress.cancel();
    myUpdateVisibleProgress = new DaemonProgressIndicator();
    myUpdateVisibleProgress.start();
  }

  public void setUpdateByTimerEnabled(boolean value) {
    myUpdateByTimerEnabled = value;
    stopProcess(true);
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

    if (!file.isPhysical()) return false;
    if (file instanceof PsiCompiledElement) return false;
    final FileType fileType = file.getFileType();
    if (fileType == StdFileTypes.GUI_DESIGNER_FORM){
      return true;
    }
    // To enable T.O.D.O. highlighting
    return !(file instanceof PsiPlainTextFile) || fileType instanceof CustomFileType;
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
    ArrayList<TextEditorHighlightingPass> result = new ArrayList<TextEditorHighlightingPass>(allPasses.size());
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

  public ProgressIndicator getUpdateProgress() {
    return myUpdateProgress;
  }

  public synchronized void stopProcess(boolean toRestartAlarm) {
    renewUpdateProgress(toRestartAlarm);
    myAlarm.cancelAllRequests();
    boolean restart = toRestartAlarm && !myDisposed && myInitialized/* && myDaemonListeners.myIsFrameFocused*/;
    if (restart) {
      myAlarm.addRequest(myUpdateRunnable, mySettings.AUTOREPARSE_DELAY);
      //LOG.debug("restarted ",new Throwable());
    }
  }

  private synchronized void renewUpdateProgress(final boolean start) {
    myUpdateProgress.cancel();
    myPassExecutorService.cancelAll();
    renewUpdateVisibleProgress();
    if (myUpdateProgress instanceof MockDaemonProgressIndicator) {
      myUpdateProgress = new MockDaemonProgressIndicator(((MockDaemonProgressIndicator)myUpdateProgress).myStoppedNotify);
    }
    else {
      DaemonProgressIndicator indicator = new DaemonProgressIndicator();
      myUpdateProgress = indicator;
      if (start) {
        indicator.start();
      }

    }
  }

  @Nullable
  public static HighlightInfo[] getHighlights(Document document, Project project) {
    LOG.assertTrue(ApplicationManager.getApplication().isReadAccessAllowed());
    MarkupModel markup = document.getMarkupModel(project);
    return markup.getUserData(HIGHLIGHTS_IN_EDITOR_DOCUMENT_KEY);
  }

  @NotNull
  public static HighlightInfo[] getHighlights(Document document, HighlightSeverity minSeverity, Project project) {
    return getHighlights(document, minSeverity, project, Integer.MIN_VALUE, Integer.MAX_VALUE);
  }

  @NotNull
  public static HighlightInfo[] getHighlights(Document document, HighlightSeverity minSeverity, Project project, int startOffset, int endOffset) {
    LOG.assertTrue(ApplicationManager.getApplication().isReadAccessAllowed());
    HighlightInfo[] highlights = getHighlights(document, project);
    if (highlights == null) return HighlightInfo.EMPTY_ARRAY;
    List<HighlightInfo> array = new ArrayList<HighlightInfo>();
    for (HighlightInfo info : highlights) {
      if (SeverityRegistrar.getInstance(project).compare(info.getSeverity(), minSeverity) >= 0 &&
          info.startOffset >= startOffset &&
          info.endOffset <= endOffset) {
        array.add(info);
      }
    }
    return array.toArray(new HighlightInfo[array.size()]);
  }

  @Nullable
  public HighlightInfo findHighlightByOffset(Document document, int offset, boolean includeFixRange) {
    HighlightInfo[] highlights = getHighlights(document, myProject);
    if (highlights == null) return null;

    List<HighlightInfo> foundInfoList = new SmartList<HighlightInfo>();
    for (HighlightInfo info : highlights) {
      if (info.highlighter == null || !info.highlighter.isValid()) continue;
      int startOffset = info.highlighter.getStartOffset();
      int endOffset = info.highlighter.getEndOffset();
      if (info.isAfterEndOfLine) {
        startOffset += 1;
        endOffset += 1;
      }
      if (startOffset > offset || offset > endOffset) {
        if (!includeFixRange) continue;
        if (info.fixMarker == null || !info.fixMarker.isValid()) continue;
        startOffset = info.fixMarker.getStartOffset();
        endOffset = info.fixMarker.getEndOffset();
        if (info.isAfterEndOfLine) {
          startOffset += 1;
          endOffset += 1;
        }
        if (startOffset > offset || offset > endOffset) continue;
      }

      if (!foundInfoList.isEmpty()) {
        HighlightInfo foundInfo = foundInfoList.get(0);
        if (SeverityRegistrar.getInstance(myProject).compare(foundInfo.getSeverity(), info.getSeverity()) < 0) {
          foundInfoList.clear();
        }
        else if (SeverityRegistrar.getInstance(myProject).compare(info.getSeverity(), foundInfo.getSeverity()) < 0) {
          continue;
        }
      }
      foundInfoList.add(info);
    }

    if (foundInfoList.isEmpty()) return null;
    if (foundInfoList.size() == 1) return foundInfoList.get(0);
    return new HighlightInfoComposite(foundInfoList);
  }

  public static void setHighlights(Document document, HighlightInfo[] highlights, Project project) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    MarkupModel markup = document.getMarkupModel(project);
    highlights = stripWarningsCoveredByErrors(highlights, markup);
    markup.putUserData(HIGHLIGHTS_IN_EDITOR_DOCUMENT_KEY, highlights);

    DaemonCodeAnalyzer codeAnalyzer = DaemonCodeAnalyzer.getInstance(project); 
    if (codeAnalyzer instanceof DaemonCodeAnalyzerImpl && ((DaemonCodeAnalyzerImpl)codeAnalyzer).myStatusBarUpdater != null) {
      ((DaemonCodeAnalyzerImpl)codeAnalyzer).myStatusBarUpdater.updateStatus();
    }
  }

  @NotNull
  private static HighlightInfo[] stripWarningsCoveredByErrors(HighlightInfo[] highlights, MarkupModel markup) {
    List<HighlightInfo> all = new ArrayList<HighlightInfo>(Arrays.asList(highlights));
    List<HighlightInfo> errors = new ArrayList<HighlightInfo>();
    for (HighlightInfo highlight : highlights) {
      if (highlight.getSeverity() == HighlightSeverity.ERROR) {
        errors.add(highlight);
      }
    }

    for (HighlightInfo highlight : highlights) {
      if (highlight.getSeverity().myVal < HighlightSeverity.ERROR.myVal &&
          highlight.getSeverity().myVal > 0) {
        for (HighlightInfo errorInfo : errors) {
          if (isCoveredBy(highlight, errorInfo)) {
            all.remove(highlight);
            RangeHighlighter highlighter = highlight.highlighter;
            if (highlighter != null && highlighter.isValid()) {
              markup.removeHighlighter(highlighter);
            }
            break;
          }
        }
      }
    }

    return all.size() < highlights.length ? all.toArray(new HighlightInfo[all.size()]) : highlights;
  }

  private static boolean isCoveredBy(HighlightInfo testInfo, HighlightInfo coveringCandidate) {
    return testInfo.startOffset <= coveringCandidate.endOffset && testInfo.endOffset >= coveringCandidate.startOffset;
  }

  @Nullable
  public static LineMarkerInfo[] getLineMarkers(Document document, Project project) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    MarkupModel markup = document.getMarkupModel(project);
    return markup.getUserData(MARKERS_IN_EDITOR_DOCUMENT_KEY);
  }

  public static void setLineMarkers(Document document, LineMarkerInfo[] lineMarkers, Project project) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    MarkupModel markup = document.getMarkupModel(project);
    markup.putUserData(MARKERS_IN_EDITOR_DOCUMENT_KEY, lineMarkers);
  }

  public synchronized void setLastIntentionHint(IntentionHintComponent hintComponent) {
    if (myLastIntentionHint != null) {
      myLastIntentionHint.hide();
    }
    myLastIntentionHint = hintComponent;
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
        if (LOG.isDebugEnabled()) {
          LOG.debug("update runnable (myUpdateByTimerEnabled = " + myUpdateByTimerEnabled + ")");
        }
        if (!myUpdateByTimerEnabled) return;
        if (myDisposed || myProject.isDisposed()) return;
        final Collection<FileEditor> activeEditors = myDaemonListeners.getSelectedEditors();
        if (activeEditors.isEmpty()) return;
        Map<FileEditor, HighlightingPass[]> passes = new THashMap<FileEditor, HighlightingPass[]>(activeEditors.size());
        //setLastIntentionHint(null);
        for (FileEditor fileEditor : activeEditors) {
          BackgroundEditorHighlighter highlighter = fileEditor.getBackgroundHighlighter();
          if (highlighter != null) {
            HighlightingPass[] highlightingPasses = highlighter.createPassesForEditor();
            passes.put(fileEditor, highlightingPasses);
          }
        }
        // cancel all after calling createPasses() since there are perverts {@link com.intellij.util.xml.ui.DomUIFactoryImpl} who are changing PSI there
        renewUpdateProgress(true);
        myAlarm.cancelAllRequests();
        myPassExecutorService.submitPasses(passes, myUpdateProgress, Job.DEFAULT_PRIORITY);
      }
    };
  }
  boolean canChangeFileSilently(PsiFile file) {
    return myDaemonListeners.canChangeFileSilently(file);
  }

  public void autoImportReferenceAtCursor(@NotNull Editor editor, @NotNull PsiFile file) {
    ShowIntentionsPass.autoImportReferenceAtCursor(editor, file);
  }
}
