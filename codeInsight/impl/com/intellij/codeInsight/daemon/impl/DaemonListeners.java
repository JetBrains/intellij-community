package com.intellij.codeInsight.daemon.impl;

import com.intellij.ProjectTopics;
import com.intellij.codeHighlighting.Pass;
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.intention.impl.IntentionHintComponent;
import com.intellij.ide.projectView.impl.nodes.PackageUtil;
import com.intellij.ide.todo.TodoConfiguration;
import com.intellij.j2ee.extResources.ExternalResourceListener;
import com.intellij.j2ee.openapi.ex.ExternalResourceManagerEx;
import com.intellij.lang.ant.config.AntBuildFile;
import com.intellij.lang.ant.config.AntConfiguration;
import com.intellij.lang.ant.config.AntConfigurationListener;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionManagerEx;
import com.intellij.openapi.actionSystem.ex.AnActionListener;
import com.intellij.openapi.application.ApplicationAdapter;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ApplicationListener;
import com.intellij.openapi.command.CommandAdapter;
import com.intellij.openapi.command.CommandEvent;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.CommandListener;
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
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootEvent;
import com.intellij.openapi.roots.ModuleRootListener;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.FileStatusManager;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vfs.*;
import com.intellij.profile.Profile;
import com.intellij.profile.ProfileChangeAdapter;
import com.intellij.profile.codeInspection.InspectionProfileManager;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.scope.packageSet.NamedScope;
import com.intellij.psi.search.scope.packageSet.NamedScopesHolder;
import com.intellij.util.messages.MessageBusConnection;
import gnu.trove.THashSet;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.Collection;
import java.util.Collections;
import java.util.List;


/**
 * @author cdr
 */
public class DaemonListeners {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.DaemonListeners");

  private final CommandListener myCommandListener = new MyCommandListener();
  private final ApplicationListener myApplicationListener = new MyApplicationListener();
  private final EditorColorsListener myEditorColorsListener = new MyEditorColorsListener();
  private final AnActionListener myAnActionListener = new MyAnActionListener();
  private final PropertyChangeListener myTodoListener = new MyTodoListener();
  private final ExternalResourceListener myExternalResourceListener = new MyExternalResourceListener();
  private final AntConfigurationListener myAntConfigurationListener = new MyAntConfigurationListener();
  private final EditorMouseMotionListener myEditorMouseMotionListener = new MyEditorMouseMotionListener();
  private final EditorMouseListener myEditorMouseListener = new MyEditorMouseListener();
  private final ProfileChangeAdapter myProfileChangeListener = new MyProfileChangeListener();

  private final DocumentListener myDocumentListener;
  private final VirtualFileListener myVirtualFileListener;
  private final EditorFactoryListener myEditorFactoryListener;

  private final CaretListener myCaretListener;
  private final Project myProject;
  private final DaemonCodeAnalyzerImpl myDaemonCodeAnalyzer;

  private boolean myEscPressed;

  private final ErrorStripeHandler myErrorStripeHandler;

  private volatile boolean cutOperationJustHappened;
  private final EditorTracker myEditorTracker;
  private final EditorTrackerListener myEditorTrackerListener;

  public DaemonListeners(Project project, DaemonCodeAnalyzerImpl daemonCodeAnalyzer, EditorTracker editorTracker) {
    myProject = project;
    myDaemonCodeAnalyzer = daemonCodeAnalyzer;

    final MessageBusConnection connection = myProject.getMessageBus().connect();
    EditorEventMulticaster eventMulticaster = EditorFactory.getInstance().getEventMulticaster();

    myDocumentListener = new DocumentAdapter() {
      public void documentChanged(DocumentEvent e) {
        Document document = e.getDocument();
        if (!worthBothering(document)) return; //no need to stop daemon if something happened in the console

        stopDaemon(true);
        UpdateHighlightersUtil.updateHighlightersByTyping(myProject, e);
      }
    };
    eventMulticaster.addDocumentListener(myDocumentListener);

    myCaretListener = new CaretListener() {
      public void caretPositionChanged(CaretEvent e) {
        Editor editor = e.getEditor();
        if (!worthBothering(editor.getDocument())) return; //no need to stop daemon if something happened in the console

        stopDaemon(true);
        IntentionHintComponent component = myDaemonCodeAnalyzer.getLastIntentionHint();
        if (component != null) {
          myDaemonCodeAnalyzer.setLastIntentionHint(null);
        }
      }
    };
    eventMulticaster.addCaretListener(myCaretListener);

    eventMulticaster.addEditorMouseMotionListener(myEditorMouseMotionListener);
    eventMulticaster.addEditorMouseListener(myEditorMouseListener);

    myEditorTracker = editorTracker;
    myEditorTrackerListener = new EditorTrackerListener() {
      public void activeEditorsChanged(List<Editor> editors) {
        if (!editors.isEmpty()) {
          stopDaemon(true);  // do not stop daemon if idea loses focus
        }
      }
    };
    myEditorTracker.addEditorTrackerListener(myEditorTrackerListener);

    myEditorFactoryListener = new EditorFactoryAdapter() {
      public void editorCreated(EditorFactoryEvent event) {
        Editor editor = event.getEditor();
        Document document = editor.getDocument();
        if (worthBothering(document)) {
          myDaemonCodeAnalyzer.repaintErrorStripeRenderer(editor);
        }
      }
    };
    EditorFactory.getInstance().addEditorFactoryListener(myEditorFactoryListener);

    PsiManager.getInstance(myProject).addPsiTreeChangeListener(new PsiChangeHandler(myProject, daemonCodeAnalyzer));

    connection.subscribe(ProjectTopics.PROJECT_ROOTS, new ModuleRootListener() {
      public void beforeRootsChange(ModuleRootEvent event) {
      }

      public void rootsChanged(ModuleRootEvent event) {
        final FileEditor[] editors = FileEditorManager.getInstance(myProject).getSelectedEditors();
        if (editors.length == 0) return;
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          public void run() {
            if (myProject.isDisposed()) return;
            for (FileEditor fileEditor : editors) {
              if (fileEditor instanceof TextEditor) {
                myDaemonCodeAnalyzer.repaintErrorStripeRenderer(((TextEditor)fileEditor).getEditor());
              }
            }
          }
        }, ModalityState.stateForComponent(editors[0].getComponent()));
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
          myDaemonCodeAnalyzer.restart();
          PsiFile psiFile = PsiManager.getInstance(myProject).findFile(event.getFile());
          if (psiFile != null && !myDaemonCodeAnalyzer.isHighlightingAvailable(psiFile)) {
            Document document = FileDocumentManager.getInstance().getCachedDocument(event.getFile());
            if (document != null) {
              // highlight markers no more
              //todo clear all highlights regardless the pass id
              UpdateHighlightersUtil.setHighlightersToEditor(myProject, document, 0, document.getTextLength(),
                                                             Collections.<HighlightInfo>emptyList(), Pass.UPDATE_ALL);
            }
          }
        }
      }
    };
    VirtualFileManager.getInstance().addVirtualFileListener(myVirtualFileListener);

    if (myProject.hasComponent(AntConfiguration.class)) {
      AntConfiguration.getInstance(myProject).addAntConfigurationListener(myAntConfigurationListener);
    }

    myErrorStripeHandler = new ErrorStripeHandler(myProject);
    ((EditorEventMulticasterEx)eventMulticaster).addErrorStripeListener(myErrorStripeHandler);

    final NamedScopesHolder[] holders = myProject.getComponents(NamedScopesHolder.class);
    NamedScopesHolder.ScopeListener scopeListener = new NamedScopesHolder.ScopeListener() {
      public void scopesChanged() {
        myDaemonCodeAnalyzer.reloadScopes();
      }
    };
    for (NamedScopesHolder holder : holders) {
      holder.addScopeListener(scopeListener);
    }
  }

  private boolean worthBothering(final Document document) {
    return PsiDocumentManager.getInstance(myProject).getPsiFile(document) != null;
  }

  public void dispose() {
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

    ((EditorEventMulticasterEx)eventMulticaster).removeErrorStripeListener(myErrorStripeHandler);
    myEditorTracker.removeEditorTrackerListener(myEditorTrackerListener);
  }

  boolean canChangeFileSilently(PsiFileSystemItem file) {
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
      if (myDaemonCodeAnalyzer.getUpdateProgress().isCanceled()) return;
      if (LOG.isDebugEnabled()) {
        LOG.debug("cancelling code highlighting by write action:" + action);
      }
      stopDaemon(false);
    }

    public void writeActionFinished(Object action) {
      if (!myDaemonCodeAnalyzer.getUpdateProgress().isRunning()) {
        stopDaemon(true);
      }
    }
  }

  private class MyCommandListener extends CommandAdapter {
    private final Object myCutActionName =
      ActionManager.getInstance().getAction(IdeActions.ACTION_EDITOR_CUT).getTemplatePresentation().getText();

    public void commandStarted(CommandEvent event) {
      Object id = event.getCommandGroupId();
      Document affectedDocument = null;

      if (id instanceof Document) affectedDocument = (Document)id;
      if (id instanceof Ref && ((Ref)id).get() instanceof Document) affectedDocument = (Document)((Ref)id).get();
      if (affectedDocument != null && !worthBothering(affectedDocument)) return;

      cutOperationJustHappened = myCutActionName.equals(event.getCommandName());
      if (myDaemonCodeAnalyzer.getUpdateProgress().isCanceled()) return;
      if (LOG.isDebugEnabled()) {
        LOG.debug("cancelling code highlighting by command:" + event.getCommand());
      }
      stopDaemon(false);
    }

    public void commandFinished(CommandEvent event) {
      Object id = event.getCommandGroupId();
      if (id instanceof Document && !worthBothering((Document)id)) return;

      if (myEscPressed) {
        myEscPressed = false;
        if (id instanceof Document) {
          Document document = (Document)id;
          // prevent Esc key to leave the document in the not-highlighted state
          if (!myDaemonCodeAnalyzer.getFileStatusMap().allDirtyScopesAreNull(document)) {
            stopDaemon(true);
          }
        }
      }
      else if (!myDaemonCodeAnalyzer.getUpdateProgress().isRunning()) {
        stopDaemon(true);
      }
    }
  }

  private class MyEditorColorsListener implements EditorColorsListener {
    public void globalSchemeChange(EditorColorsScheme scheme) {
      myDaemonCodeAnalyzer.restart();
    }
  }

  private class MyTodoListener implements PropertyChangeListener {
    public void propertyChange(PropertyChangeEvent evt) {
      if (TodoConfiguration.PROP_TODO_PATTERNS.equals(evt.getPropertyName())) {
        myDaemonCodeAnalyzer.restart();
      }
    }
  }

  private class MyProfileChangeListener extends ProfileChangeAdapter {
    public void profileChanged(Profile profile) {
      myDaemonCodeAnalyzer.restart();
    }

    public void profileActivated(NamedScope scope, Profile oldProfile, Profile profile) {
      myDaemonCodeAnalyzer.restart();
    }
  }

  private class MyAnActionListener implements AnActionListener {
    private final AnAction escapeAction = ActionManager.getInstance().getAction(IdeActions.ACTION_EDITOR_ESCAPE);

    public void beforeActionPerformed(AnAction action, DataContext dataContext) {
      myEscPressed = action == escapeAction;
    }

    public void afterActionPerformed(final AnAction action, final DataContext dataContext) {
    }

    public void beforeEditorTyping(char c, DataContext dataContext) {
      if (DataKeys.PSI_FILE.getData(dataContext) == null) return; //no need to stop daemon if something happened in the console

      stopDaemon(true);
    }
  }

  private class MyExternalResourceListener implements ExternalResourceListener {
    public void externalResourceChanged() {
      myDaemonCodeAnalyzer.restart();
    }
  }

  private class MyAntConfigurationListener implements AntConfigurationListener {
    public void configurationLoaded() {
      myDaemonCodeAnalyzer.restart();
    }

    public void buildFileChanged(final AntBuildFile buildFile) {
      myDaemonCodeAnalyzer.restart();
    }

    public void buildFileAdded(final AntBuildFile buildFile) {
      myDaemonCodeAnalyzer.restart();
    }

    public void buildFileRemoved(final AntBuildFile buildFile) {
      myDaemonCodeAnalyzer.restart();
    }
  }

  private static class MyEditorMouseListener extends EditorMouseAdapter {

    public void mouseExited(EditorMouseEvent e) {
      if (!HintManager.getInstance().getTooltipController().shouldSurvive(e.getMouseEvent())) {
        DaemonTooltipUtil.cancelTooltips();
      }
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
          HighlightInfo info = myDaemonCodeAnalyzer.findHighlightByOffset(editor.getDocument(), offset, false);
          if (info == null || info.description == null) return;
          DaemonTooltipUtil.showInfoTooltip(info, editor, offset);
          shown = true;
        }
      }
      finally {
        if (!shown && !HintManager.getInstance().getTooltipController().shouldSurvive(e.getMouseEvent())) {
          DaemonTooltipUtil.cancelTooltips();
        }
      }
    }

    public void mouseDragged(EditorMouseEvent e) {
      HintManager.getInstance().getTooltipController().cancelTooltips();
    }
  }

  private void stopDaemon(boolean toRestartAlarm) {
    myDaemonCodeAnalyzer.stopProcess(toRestartAlarm);
  }

  Collection<FileEditor> getSelectedEditors() {
    // Editors in modal context
    List<Editor> editors = myEditorTracker.getActiveEditors();

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
}
