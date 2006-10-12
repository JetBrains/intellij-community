package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeHighlighting.BackgroundEditorHighlighter;
import com.intellij.codeHighlighting.HighlightingPass;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzerSettings;
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.intention.impl.IntentionHintComponent;
import com.intellij.codeInsight.problems.WolfTheProblemSolverImpl;
import com.intellij.ide.highlighter.custom.impl.CustomFileType;
import com.intellij.ide.projectView.impl.nodes.PackageUtil;
import com.intellij.ide.todo.TodoConfiguration;
import com.intellij.j2ee.extResources.ExternalResourceListener;
import com.intellij.j2ee.openapi.ex.ExternalResourceManagerEx;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.lang.ant.config.AntBuildFile;
import com.intellij.lang.ant.config.AntConfiguration;
import com.intellij.lang.ant.config.AntConfigurationListener;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.actionSystem.ex.ActionManagerEx;
import com.intellij.openapi.actionSystem.ex.AnActionListener;
import com.intellij.openapi.application.ApplicationAdapter;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.command.CommandAdapter;
import com.intellij.openapi.command.CommandEvent;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.colors.EditorColorsListener;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.event.*;
import com.intellij.openapi.editor.ex.EditorEventMulticasterEx;
import com.intellij.openapi.editor.ex.EditorMarkupModel;
import com.intellij.openapi.editor.markup.MarkupModel;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerAdapter;
import com.intellij.openapi.roots.ModuleRootEvent;
import com.intellij.openapi.roots.ModuleRootListener;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.*;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.FileStatusManager;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileAdapter;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.VirtualFilePropertyEvent;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.wm.ex.WindowManagerEx;
import com.intellij.openapi.wm.impl.IdeFrame;
import com.intellij.problems.WolfTheProblemSolver;
import com.intellij.profile.Profile;
import com.intellij.profile.ProfileChangeAdapter;
import com.intellij.profile.codeInspection.InspectionProfileManager;
import com.intellij.psi.*;
import com.intellij.psi.search.scope.packageSet.NamedScope;
import com.intellij.psi.search.scope.packageSet.NamedScopesHolder;
import com.intellij.util.Alarm;
import com.intellij.util.SmartList;
import com.intellij.util.concurrency.Semaphore;
import gnu.trove.THashSet;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.event.WindowEvent;
import java.awt.event.WindowFocusListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
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
  private EditorTracker myEditorTracker;

  private final DaemonProgress myUpdateProgress = new DaemonProgress();

  private final Semaphore myUpdateThreadSemaphore = new Semaphore();

  private final Runnable myUpdateRunnable = createUpdateRunnable();

  private final Alarm myAlarm = new Alarm();
  private boolean myUpdateByTimerEnabled = true;
  private final Set<VirtualFile> myDisabledHintsFiles = new THashSet<VirtualFile>();
  private final Set<PsiFile> myDisabledHighlightingFiles = new THashSet<PsiFile>();

  private final FileStatusMap myFileStatusMap;

  private StatusBarUpdater myStatusBarUpdater;

  private DaemonCodeAnalyzerSettings myLastSettings;

  private final MyCommandListener myCommandListener = new MyCommandListener();
  private final MyApplicationListener myApplicationListener = new MyApplicationListener();
  private final EditorColorsListener myEditorColorsListener = new MyEditorColorsListener();
  private final AnActionListener myAnActionListener = new MyAnActionListener();
  private final PropertyChangeListener myTodoListener = new MyTodoListener();
  private final ExternalResourceListener myExternalResourceListener = new MyExternalResourceListener();
  private final AntConfigurationListener myAntConfigurationListener = new MyAntConfigurationListener();
  private final EditorMouseMotionListener myEditorMouseMotionListener = new MyEditorMouseMotionListener();
  private final EditorMouseListener myEditorMouseListener = new MyEditorMouseListener();
  private final ProfileChangeAdapter myProfileChangeListener = new MyProfileChangeListener();

  private final WindowFocusListener myIdeFrameFocusListener = new MyWindowFocusListener();

  private DocumentListener myDocumentListener;
  private CaretListener myCaretListener;
  private ErrorStripeHandler myErrorStripeHandler;
  //private long myUpdateStartTime;

  private boolean myEscPressed;
  private EditorFactoryListener myEditorFactoryListener;

  private boolean myShowPostIntentions = true;
  private IntentionHintComponent myLastIntentionHint;

  private boolean myDisposed;
  private boolean myInitialized;

  private boolean myIsFrameFocused = true;
  @NonNls private static final String DISABLE_HINTS_TAG = "disable_hints";
  @NonNls private static final String FILE_TAG = "file";
  @NonNls private static final String URL_ATT = "url";

  private boolean cutOperationJustHappened;
  private VirtualFileAdapter myVirtualFileListener;

  protected DaemonCodeAnalyzerImpl(Project project, DaemonCodeAnalyzerSettings daemonCodeAnalyzerSettings) {
    myProject = project;

    mySettings = daemonCodeAnalyzerSettings;
    myLastSettings = (DaemonCodeAnalyzerSettings)mySettings.clone();

    myFileStatusMap = new FileStatusMap(myProject);
  }

  @NotNull
  public String getComponentName() {
    return "DaemonCodeAnalyzer";
  }

  public void initComponent() {
  }

  public void disposeComponent() {
    myFileStatusMap.markAllFilesDirty();
    dispose();
  }

  public EditorTracker getEditorTracker() {
    return myEditorTracker;
  }

  public void projectOpened() {
    EditorEventMulticaster eventMulticaster = EditorFactory.getInstance().getEventMulticaster();

    myDocumentListener = new DocumentAdapter() {
      public void documentChanged(DocumentEvent e) {
        stopProcess(true);
        UpdateHighlightersUtil.updateHighlightersByTyping(myProject, e);
      }
    };
    eventMulticaster.addDocumentListener(myDocumentListener);

    myCaretListener = new CaretListener() {
      public void caretPositionChanged(CaretEvent e) {
        stopProcess(true);
      }
    };
    eventMulticaster.addCaretListener(myCaretListener);

    eventMulticaster.addEditorMouseMotionListener(myEditorMouseMotionListener);
    eventMulticaster.addEditorMouseListener(myEditorMouseListener);

    myEditorTracker = createEditorTracker();
    myEditorTracker.addEditorTrackerListener(new EditorTrackerListener() {
      public void activeEditorsChanged(final Editor[] editors) {
        if (editors.length > 0) {
          myIsFrameFocused = true; // Happens when debugger evaluation window gains focus out of main frame.
        }
        stopProcess(true);
      }
    });

    myEditorFactoryListener = new EditorFactoryAdapter() {
      public void editorCreated(EditorFactoryEvent event) {
        Editor editor = event.getEditor();
        Document document = editor.getDocument();
        PsiFile file = PsiDocumentManager.getInstance(myProject).getPsiFile(document);
        if (file != null) {
          ((EditorMarkupModel)editor.getMarkupModel()).setErrorStripeRenderer(
            new RefreshStatusRenderer(myProject, DaemonCodeAnalyzerImpl.this, document, file));
        }
      }
    };
    EditorFactory.getInstance().addEditorFactoryListener(myEditorFactoryListener);

    PsiManager.getInstance(myProject).addPsiTreeChangeListener(new PsiChangeHandler(myProject, this));
    ProjectRootManager.getInstance(myProject).addModuleRootListener(new ModuleRootListener() {
      public void beforeRootsChange(ModuleRootEvent event) {
      }

      public void rootsChanged(ModuleRootEvent event) {
        final FileEditor[] editors = FileEditorManager.getInstance(myProject).getSelectedEditors();
        if (editors != null) {
          for (FileEditor fileEditor : editors) {
            if (fileEditor instanceof TextEditor) {
              final Editor editor = ((TextEditor)fileEditor).getEditor();
              ApplicationManager.getApplication().invokeLater(new Runnable() {
                public void run() {
                  if (myProject.isDisposed()) return;

                  final Document document = editor.getDocument();
                  final PsiFile psiFile = PsiDocumentManager.getInstance(myProject).getPsiFile(document);
                  ((EditorMarkupModel)editor.getMarkupModel()).setErrorStripeRenderer(new RefreshStatusRenderer(myProject, DaemonCodeAnalyzerImpl.this, document, psiFile));
                }
              }, ModalityState.stateForComponent(editor.getComponent()));
            }
          }
        }
      }
    });

    CommandProcessor.getInstance().addCommandListener(myCommandListener);
    ApplicationManager.getApplication().addApplicationListener(myApplicationListener);
    EditorColorsManager.getInstance().addEditorColorsListener(myEditorColorsListener);
    InspectionProfileManager.getInstance().addProfileChangeListener(myProfileChangeListener);
    TodoConfiguration.getInstance().addPropertyChangeListener(myTodoListener);
    ActionManagerEx.getInstanceEx().addAnActionListener(myAnActionListener);
    ExternalResourceManagerEx.getInstanceEx().addExteralResourceListener(myExternalResourceListener);
    myVirtualFileListener = new VirtualFileAdapter() {
      public void propertyChanged(VirtualFilePropertyEvent event) {
        if (VirtualFile.PROP_NAME.equals(event.getPropertyName())) {
          restart();
          PsiFile psiFile = PsiManager.getInstance(myProject).findFile(event.getFile());
          if (psiFile != null && !isHighlightingAvailable(psiFile)) {
            Document document = FileDocumentManager.getInstance().getCachedDocument(event.getFile());
            if (document != null) {
              // highlight markers no more
              UpdateHighlightersUtil.setHighlightersToEditor(myProject, document, 0, document.getTextLength(), Collections.<HighlightInfo>emptyList(), UpdateHighlightersUtil.NORMAL_HIGHLIGHTERS_GROUP);
            }
          }
        }
      }
    };
    VirtualFileManager.getInstance().addVirtualFileListener(myVirtualFileListener);

    if (myProject.hasComponent(AntConfiguration.class)) {
      AntConfiguration.getInstance(myProject).addAntConfigurationListener(myAntConfigurationListener);
    }

    myStatusBarUpdater = new StatusBarUpdater(myProject);

    myErrorStripeHandler = new ErrorStripeHandler(myProject);
    ((EditorEventMulticasterEx)eventMulticaster).addErrorStripeListener(myErrorStripeHandler);

    ProjectManager.getInstance().addProjectManagerListener(
      myProject,
      new ProjectManagerAdapter() {
        public void projectClosing(Project project) {
          dispose();
        }
      }
    );

    IdeFrame frame = ((WindowManagerEx)WindowManager.getInstance()).getFrame(myProject);
    if (frame != null) {
      frame.addWindowFocusListener(myIdeFrameFocusListener);
    }

    final NamedScopesHolder[] holders = myProject.getComponents(NamedScopesHolder.class);
    NamedScopesHolder.ScopeListener scopeListener = new NamedScopesHolder.ScopeListener() {
      public void scopesChanged() {
        reloadScopes();
      }
    };
    for (NamedScopesHolder holder : holders) {
      holder.addScopeListener(scopeListener);
    }
    reloadScopes();

    myInitialized = true;
  }

  private List<Pair<NamedScope, NamedScopesHolder>> myScopes = Collections.emptyList();
  private void reloadScopes() {
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

  public void projectClosed() {
  }

  private void dispose() {
    if (myDisposed) return;
    if (myInitialized) {
      EditorEventMulticaster eventMulticaster = EditorFactory.getInstance().getEventMulticaster();
      eventMulticaster.removeDocumentListener(myDocumentListener);
      eventMulticaster.removeCaretListener(myCaretListener);
      eventMulticaster.removeEditorMouseMotionListener(myEditorMouseMotionListener);
      eventMulticaster.removeEditorMouseListener(myEditorMouseListener);

      EditorFactory.getInstance().removeEditorFactoryListener(myEditorFactoryListener);
      CommandProcessor.getInstance().removeCommandListener(myCommandListener);
      ApplicationManager.getApplication().removeApplicationListener(myApplicationListener);
      EditorColorsManager.getInstance().removeEditorColorsListener(myEditorColorsListener);
      InspectionProfileManager.getInstance().removeProfileChangeListener(myProfileChangeListener);
      TodoConfiguration.getInstance().removePropertyChangeListener(myTodoListener);
      ActionManagerEx.getInstanceEx().removeAnActionListener(myAnActionListener);
      ExternalResourceManagerEx.getInstanceEx().removeExternalResourceListener(myExternalResourceListener);
      VirtualFileManager.getInstance().removeVirtualFileListener(myVirtualFileListener);

      if (myProject.hasComponent(AntConfiguration.class)) {
        AntConfiguration.getInstance(myProject).removeAntConfigurationListener(myAntConfigurationListener);
      }

      myStatusBarUpdater.dispose();
      myEditorTracker.dispose();

      ((EditorEventMulticasterEx)eventMulticaster).removeErrorStripeListener(myErrorStripeHandler);
      IdeFrame frame = ((WindowManagerEx)WindowManager.getInstance()).getFrame(myProject);
      if (frame != null) {
        frame.removeWindowFocusListener(myIdeFrameFocusListener);
      }
    }

    // clear dangling references to PsiFiles/Documents. SCR#10358
    myFileStatusMap.markAllFilesDirty();

    myDisposed = true;

    stopProcess(false);
    myUpdateThreadSemaphore.waitFor();
    myLastSettings = null;
  }

  public void settingsChanged() {
    DaemonCodeAnalyzerSettings settings = DaemonCodeAnalyzerSettings.getInstance();
    if (settings.isCodeHighlightingChanged(myLastSettings)) {
      restart();
    }
    myLastSettings = (DaemonCodeAnalyzerSettings)settings.clone();
  }

  public void updateVisibleHighlighters(Editor editor) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    if (ApplicationManager.getApplication().isUnitTestMode()) return;
    setShowPostIntentions(false);

    TextEditor textEditor = TextEditorProvider.getInstance().getTextEditor(editor);
    BackgroundEditorHighlighter highlighter = textEditor.getBackgroundHighlighter();
    if (highlighter == null) return;
    updateHighlighters(textEditor, new LinkedHashSet<HighlightingPass>(Arrays.asList(highlighter.createPassesForVisibleArea())), null);
  }

  private void updateAll(FileEditor editor, Runnable postRunnable) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    if (LOG.isDebugEnabled()) {
      /* TODO:
      PsiFile file = PsiDocumentManager.getInstance(myProject).getPsiFile(editor.getDocument());
      LOG.debug("updateAll for " + file);
      */
    }
    //myUpdateStartTime = System.currentTimeMillis();
    //Statistics.clear();

    boolean editorHiddenByModelDialog = ModalityState.current().dominates(ModalityState.stateForComponent(editor.getComponent()));
    if (editorHiddenByModelDialog) {
      stopProcess(true);
      return;
    }

    BackgroundEditorHighlighter highlighter = editor.getBackgroundHighlighter();
    final HighlightingPass[] passes = highlighter == null ? HighlightingPass.EMPTY_ARRAY : highlighter.createPassesForEditor();
    updateHighlighters(editor, new LinkedHashSet<HighlightingPass>(Arrays.asList(passes)), postRunnable);
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

  public boolean isErrorAnalyzingFinished(PsiFile file) {
    if (myDisposed) return false;
    Document document = PsiDocumentManager.getInstance(myProject).getCachedDocument(file);
    return document != null &&
           document.getModificationStamp() == file.getModificationStamp() &&
           myFileStatusMap.getFileDirtyScope(document, FileStatusMap.NORMAL_HIGHLIGHTERS) == null;
  }

  public boolean isInspectionCompleted(PsiFile file) {
    if (file instanceof PsiCompiledElement) return true;
    Document document = PsiDocumentManager.getInstance(myProject).getDocument(file);
    return document != null &&
           document.getModificationStamp() == file.getModificationStamp() &&
           myFileStatusMap.getFileDirtyScope(document, FileStatusMap.LOCAL_INSPECTIONS) == null;
  }

  public FileStatusMap getFileStatusMap() {
    return myFileStatusMap;
  }

  public DaemonProgress getUpdateProgress() {
    return myUpdateProgress;
  }

  public void stopProcess(boolean toRestartAlarm) {
    myAlarm.cancelAllRequests();
    if (toRestartAlarm && !myDisposed && myInitialized && myIsFrameFocused) {
      //LOG.assertTrue(!ApplicationManager.getApplication().isUnitTestMode());
      myAlarm.addRequest(myUpdateRunnable, mySettings.AUTOREPARSE_DELAY);
    }
    myUpdateProgress.cancel();
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
    ArrayList<HighlightInfo> array = new ArrayList<HighlightInfo>();
    for (HighlightInfo info : highlights) {
      if (info.getSeverity().compareTo(minSeverity) >= 0 &&
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
        if (foundInfo.getSeverity().compareTo(info.getSeverity()) < 0) {
          foundInfoList.clear();
        }
        else if (info.getSeverity().compareTo(foundInfo.getSeverity()) < 0) {
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

  public void setShowPostIntentions(boolean status) {
    myShowPostIntentions = status;
  }

  public boolean showPostIntentions() {
    return myShowPostIntentions;
  }

  public void setLastIntentionHint(IntentionHintComponent hintComponent) {
    myLastIntentionHint = hintComponent;
  }

  public IntentionHintComponent getLastIntentionHint() {
    return myLastIntentionHint;
  }

  private void updateHighlighters(final FileEditor editor, final Set<? extends HighlightingPass> passesToPerform, final Runnable postRunnable) {
    ApplicationManager.getApplication().assertIsDispatchThread();

    if (myUpdateProgress.isRunning()) return;
    if (passesToPerform.isEmpty()) {
      if (postRunnable != null) postRunnable.run();
      return;
    }

    final HighlightingPass daemonPass = passesToPerform.iterator().next();

    Runnable postRunnable1 = new Runnable() {
      public void run() {
        final boolean wasCanceled = myUpdateProgress.isCanceled();
        final boolean wasRunning = myUpdateProgress.isRunning();

        myUpdateThreadSemaphore.up();
        if (editor != null) {
          ApplicationManager.getApplication().invokeLater(new Runnable() {
            public void run() {
              if (myDisposed) return;
              if (myProject.isDisposed()) return;

              if (!wasCanceled || wasRunning) {
                if (daemonPass != null && editor.getComponent().isDisplayable()) {
                  daemonPass.applyInformationToEditor();
                }
                passesToPerform.remove(daemonPass);
                updateHighlighters(editor, passesToPerform, postRunnable);
              }
            }
          }, ModalityState.stateForComponent(editor.getComponent()));
        }
      }
    };

    UpdateThread updateThread;
    synchronized (myUpdateProgress) {
      if (myUpdateProgress.isRunning()) return; //Last check to be sure we don't launch 2 threads
      updateThread = new UpdateThread(daemonPass, myProject, postRunnable1); //After the call myUpdateProgress.isRunning()
    }
    myUpdateThreadSemaphore.down();
    updateThread.start();
  }


  public void writeExternal(Element parentNode) throws WriteExternalException {
    Element disableHintsElement = new Element(DISABLE_HINTS_TAG);
    parentNode.addContent(disableHintsElement);

    ArrayList<String> array = new ArrayList<String>();
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

  boolean canChangeFileSilently(PsiFile file) {
    if (cutOperationJustHappened) return false;
    VirtualFile virtualFile = file.getVirtualFile();
    if (virtualFile == null) return false;
    Project project = file.getProject();
    if (!PackageUtil.projectContainsFile(project, virtualFile, false)) return false;
    AbstractVcs activeVcs = ProjectLevelVcsManager.getInstance(project).getVcsFor(virtualFile);
    if (activeVcs == null) return true;
    FileStatus status = FileStatusManager.getInstance(project).getStatus(virtualFile);

    return status != FileStatus.NOT_CHANGED;
  }

  private class MyApplicationListener extends ApplicationAdapter {
    public void beforeWriteActionStart(Object action) {
      if (!myUpdateProgress.isRunning()) return;
      if (myUpdateProgress.isCanceled()) return;
      if (LOG.isDebugEnabled()) {
        LOG.debug("cancelling code highlighting by write action:" + action);
      }
      stopProcess(false);
    }

    public void writeActionFinished(Object action) {
      stopProcess(true);
    }
  }

  private class MyCommandListener extends CommandAdapter {
    private final String myCutActionName = ActionManager.getInstance().getAction(IdeActions.ACTION_EDITOR_CUT).getTemplatePresentation().getText();

    public void commandStarted(CommandEvent event) {
      cutOperationJustHappened = myCutActionName.equals(event.getCommandName());
      if (!myUpdateProgress.isRunning()) return;
      if (myUpdateProgress.isCanceled()) return;
      if (LOG.isDebugEnabled()) {
        LOG.debug("cancelling code highlighting by command:" + event.getCommand());
      }
      stopProcess(false);
    }

    public void commandFinished(CommandEvent event) {
      if (!myEscPressed) {
        stopProcess(true);
      }
      else {
        myEscPressed = false;
      }
    }
  }

  private class MyEditorColorsListener implements EditorColorsListener {
    public void globalSchemeChange(EditorColorsScheme scheme) {
      restart();
    }
  }

  private class MyTodoListener implements PropertyChangeListener {
    public void propertyChange(PropertyChangeEvent evt) {
      if (TodoConfiguration.PROP_TODO_PATTERNS.equals(evt.getPropertyName())) {
        restart();
      }
    }
  }

  private class MyProfileChangeListener extends ProfileChangeAdapter{
    public void profileChanged(Profile profile) {
      restart();
    }

    public void profileActivated(NamedScope scope, Profile oldProfile, Profile profile) {
      restart();
    }
  }

  private class MyAnActionListener implements AnActionListener {
    public void beforeActionPerformed(AnAction action, DataContext dataContext) {
      AnAction escapeAction = ActionManagerEx.getInstanceEx().getAction(IdeActions.ACTION_EDITOR_ESCAPE);
      if (action != escapeAction) {
        stopProcess(true);
        myEscPressed = false;
      }
      else {
        myEscPressed = true;
      }
    }

    public void beforeEditorTyping(char c, DataContext dataContext) {
      stopProcess(true);
      myEscPressed = false;
    }
  }

  private class MyExternalResourceListener implements ExternalResourceListener {
    public void externalResourceChanged() {
      restart();
    }
  }

  private class MyAntConfigurationListener implements AntConfigurationListener {
    public void buildFileChanged(final AntBuildFile buildFile) {
      restart();
    }

    public void buildFileAdded(final AntBuildFile buildFile) {
      restart();
    }

    public void buildFileRemoved(final AntBuildFile buildFile) {
      restart();
    }
  }

  private static class WolfPass implements HighlightingPass {
    private Project myProject;

    public WolfPass(final Project project) {
      myProject = project;
    }

    public void collectInformation(ProgressIndicator progress) {
      final WolfTheProblemSolver solver = WolfTheProblemSolver.getInstance(myProject);
      if (solver instanceof WolfTheProblemSolverImpl) {
        ((WolfTheProblemSolverImpl)solver).startCheckingIfVincentSolvedProblemsYet(progress);
      }
    }

    public void applyInformationToEditor() {
    }
  }

  private Runnable createUpdateRunnable() {
    return new Runnable() {
      public void run() {
        if (LOG.isDebugEnabled()) {
          LOG.debug("update runnable (myUpdateByTimerEnabled = " + myUpdateByTimerEnabled + ")");
        }
        if (!myUpdateByTimerEnabled) return;
        if (myDisposed) return;

        final FileEditor[] activeEditors = getSelectedEditors();
        if (activeEditors.length == 0) {
          if (LOG.isDebugEnabled()) {
            LOG.debug("no active editors");
          }
          return;
        }

        class CallWolfRunnable implements Runnable {
          public void run() {
            updateHighlighters(null, Collections.singleton(new WolfPass(myProject)), null);
          }
        }

        class UpdateEditorRunnable implements Runnable {
          private int editorIndex;

          UpdateEditorRunnable(int editorIndex) {
            this.editorIndex = editorIndex;
          }

          public void run() {
            Runnable postRunnable = editorIndex < activeEditors.length - 1
                                                ? new UpdateEditorRunnable(editorIndex + 1)
                                                : new CallWolfRunnable();

            updateAll(activeEditors[editorIndex], postRunnable);
          }
        }

        new UpdateEditorRunnable(0).run();
      }
    };
  }

  private FileEditor[] getSelectedEditors() {
    // Editors in modal context
    Editor[] editors = myEditorTracker.getActiveEditors();
    FileEditor[] fileEditors = new FileEditor[editors.length];
    if (editors.length > 0) {
      for (int i = 0; i < fileEditors.length; i++) {
        fileEditors[i] = TextEditorProvider.getInstance().getTextEditor(editors[i]);
      }
    }

    if (ApplicationManager.getApplication().getCurrentModalityState() != ModalityState.NON_MODAL) {
      return fileEditors;
    }

    final FileEditor[] tabEditors = FileEditorManager.getInstance(myProject).getSelectedEditors();
    if (fileEditors.length == 0) return tabEditors;

    // Editors in tabs.
    Set<FileEditor> common = new HashSet<FileEditor>(Arrays.asList(fileEditors));
    common.addAll(Arrays.asList(tabEditors));

    return common.toArray(new FileEditor[common.size()]);
  }

  /**
   * @fabrique used in fabrique *
   */
  private EditorTracker createEditorTracker() {
    return new EditorTracker(myProject);
  }

  private static class MyEditorMouseListener extends EditorMouseAdapter{

    public void mouseExited(EditorMouseEvent e) {
      DaemonTooltipUtil.cancelTooltips();
    }
  }

  private class MyEditorMouseMotionListener implements EditorMouseMotionListener {
    public void mouseMoved(EditorMouseEvent e) {
      Editor editor = e.getEditor();
      if (myProject != editor.getProject()) return;

      boolean shown = false;
      try {
        LogicalPosition pos = editor.xyToLogicalPosition(e.getMouseEvent().getPoint());
        if (e.getArea() == EditorMouseEventArea.EDITING_AREA) {
          int offset = editor.logicalPositionToOffset(pos);
          if (editor.offsetToLogicalPosition(offset).column != pos.column) return; // we are in virtual space
          HighlightInfo info = findHighlightByOffset(editor.getDocument(), offset, false);
          if (info == null || info.description == null) return;
          DaemonTooltipUtil.showInfoTooltip(info, editor, offset);
          shown = true;
        }
      }
      finally {
        if (!shown) {
          DaemonTooltipUtil.cancelTooltips();
        }
      }
    }

    public void mouseDragged(EditorMouseEvent e) {
      HintManager.getInstance().getTooltipController().cancelTooltips();
    }
  }

  private class MyWindowFocusListener implements WindowFocusListener {
    public void windowGainedFocus(WindowEvent e) {
      if (LOG.isDebugEnabled()) {
        LOG.debug("windowGainedFocus for IdeFrame");
      }
      myIsFrameFocused = true;
      stopProcess(true);
    }

    public void windowLostFocus(WindowEvent e) {
      if (LOG.isDebugEnabled()) {
        LOG.debug("windowLostFocus for IdeFrame");
      }
      myIsFrameFocused = false;
      stopProcess(false);
    }
  }
}
