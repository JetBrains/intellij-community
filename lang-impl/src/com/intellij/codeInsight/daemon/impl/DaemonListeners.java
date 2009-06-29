package com.intellij.codeInsight.daemon.impl;

import com.intellij.ProjectTopics;
import com.intellij.codeHighlighting.Pass;
import com.intellij.codeInsight.hint.TooltipController;
import com.intellij.ide.todo.TodoConfiguration;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionManagerEx;
import com.intellij.openapi.actionSystem.ex.AnActionListener;
import com.intellij.openapi.application.*;
import com.intellij.openapi.application.impl.LaterInvocator;
import com.intellij.openapi.command.CommandAdapter;
import com.intellij.openapi.command.CommandEvent;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.*;
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
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.roots.ModuleRootEvent;
import com.intellij.openapi.roots.ModuleRootListener;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.FileStatusManager;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileAdapter;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.VirtualFilePropertyEvent;
import com.intellij.profile.Profile;
import com.intellij.profile.ProfileChangeAdapter;
import com.intellij.profile.codeInspection.InspectionProfileManager;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiDocumentManagerImpl;
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
public class DaemonListeners implements Disposable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.DaemonListeners");

  private final ApplicationListener myApplicationListener = new MyApplicationListener();
  private final EditorColorsListener myEditorColorsListener = new MyEditorColorsListener();
  private final PropertyChangeListener myTodoListener = new MyTodoListener();

  private final EditorFactoryListener myEditorFactoryListener;

  private final Project myProject;
  private final DaemonCodeAnalyzerImpl myDaemonCodeAnalyzer;
  private final ModalityStateListener myModalityStateListener;

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

    eventMulticaster.addDocumentListener(new DocumentAdapter() {
      // clearing highlighters before changing document because change can damage editor highlighters drastically, so we'll clear more than necessary
      public void beforeDocumentChange(final DocumentEvent e) {
        Document document = e.getDocument();
        VirtualFile virtualFile = FileDocumentManager.getInstance().getFile(document);
        if (!worthBothering(document, virtualFile == null ? null : ProjectUtil.guessProjectForFile(virtualFile))) {
          return; //no need to stop daemon if something happened in the console
        }
        stopDaemon(true);
        UpdateHighlightersUtil.updateHighlightersByTyping(myProject, e);
      }
    }, this);

    eventMulticaster.addCaretListener(new CaretListener() {
      public void caretPositionChanged(CaretEvent e) {
        Editor editor = e.getEditor();
        if (!worthBothering(editor.getDocument(), editor.getProject())) {
          return; //no need to stop daemon if something happened in the console
        }

        stopDaemon(true);
        myDaemonCodeAnalyzer.setLastIntentionHint(null);
      }
    }, this);

    eventMulticaster.addEditorMouseMotionListener(new MyEditorMouseMotionListener(), this);
    eventMulticaster.addEditorMouseListener(new MyEditorMouseListener(), this);

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
        if (!worthBothering(document, editor.getProject())) {
          return;
        }
        myDaemonCodeAnalyzer.repaintErrorStripeRenderer(editor);
      }
    };
    EditorFactory.getInstance().addEditorFactoryListener(myEditorFactoryListener);

    PsiDocumentManagerImpl documentManager = (PsiDocumentManagerImpl)PsiDocumentManager.getInstance(myProject);
    PsiChangeHandler changeHandler = new PsiChangeHandler(myProject, daemonCodeAnalyzer, documentManager, EditorFactory.getInstance(),connection);
    Disposer.register(this, changeHandler);
    PsiManager.getInstance(myProject).addPsiTreeChangeListener(changeHandler, changeHandler);

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

    connection.subscribe(DumbService.DUMB_MODE, new DumbService.DumbModeListener() {
      public void beforeEnteringDumbMode() {
        stopDaemon(true);
      }

      public void enteredDumbMode() {
        stopDaemon(true);
      }

      public void exitDumbMode() {
        stopDaemon(true);
      }
    });

    CommandProcessor.getInstance().addCommandListener(new MyCommandListener(), this);
    ApplicationManager.getApplication().addApplicationListener(myApplicationListener);
    EditorColorsManager.getInstance().addEditorColorsListener(myEditorColorsListener);
    InspectionProfileManager.getInstance().addProfileChangeListener(new MyProfileChangeListener(), this);
    TodoConfiguration.getInstance().addPropertyChangeListener(myTodoListener);
    ActionManagerEx.getInstanceEx().addAnActionListener(new MyAnActionListener(), this);
    VirtualFileManager.getInstance().addVirtualFileListener(new VirtualFileAdapter() {
      public void propertyChanged(VirtualFilePropertyEvent event) {
        if (VirtualFile.PROP_NAME.equals(event.getPropertyName())) {
          myDaemonCodeAnalyzer.restart();
          PsiFile psiFile = PsiManager.getInstance(myProject).findFile(event.getFile());
          if (psiFile != null && !myDaemonCodeAnalyzer.isHighlightingAvailable(psiFile)) {
            Document document = FileDocumentManager.getInstance().getCachedDocument(event.getFile());
            if (document != null) {
              // highlight markers no more
              //todo clear all highlights regardless the pass id
              UpdateHighlightersUtil
                .setHighlightersToEditor(myProject, document, 0, document.getTextLength(), Collections.<HighlightInfo>emptyList(),
                                         Pass.UPDATE_ALL);
            }
          }
        }
      }
    }, this);

    myErrorStripeHandler = new ErrorStripeHandler(myProject);
    ((EditorEventMulticasterEx)eventMulticaster).addErrorStripeListener(myErrorStripeHandler);

    final NamedScopesHolder[] holders = NamedScopesHolder.getAllNamedScopeHolders(project);
    NamedScopesHolder.ScopeListener scopeListener = new NamedScopesHolder.ScopeListener() {
      public void scopesChanged() {
        myDaemonCodeAnalyzer.reloadScopes();
      }
    };
    for (NamedScopesHolder holder : holders) {
      holder.addScopeListener(scopeListener);
    }

    myModalityStateListener = new ModalityStateListener() {
      public void beforeModalityStateChanged() {
        // before showing dialog we are in non-modal context yet, and before closing dialog we are still in modal context
        stopDaemon(LaterInvocator.isInModalContext());
      }
    };
    LaterInvocator.addModalityStateListener(myModalityStateListener);
  }

  private boolean worthBothering(final Document document, Project project) {
    if (document == null) return true;
    if (project != null && project != myProject) return false;
    // cached is essential here since we do not want to create PSI file in alien project
    PsiFile psiFile = PsiDocumentManager.getInstance(project == null ? myProject : project).getPsiFile(document);
    return psiFile != null;
  }

  public void dispose() {
    EditorEventMulticaster eventMulticaster = EditorFactory.getInstance().getEventMulticaster();

    EditorFactory.getInstance().removeEditorFactoryListener(myEditorFactoryListener);
    ApplicationManager.getApplication().removeApplicationListener(myApplicationListener);
    EditorColorsManager.getInstance().removeEditorColorsListener(myEditorColorsListener);
    TodoConfiguration.getInstance().removePropertyChangeListener(myTodoListener);

    ((EditorEventMulticasterEx)eventMulticaster).removeErrorStripeListener(myErrorStripeHandler);
    myEditorTracker.removeEditorTrackerListener(myEditorTrackerListener);
    LaterInvocator.removeModalityStateListener(myModalityStateListener);
  }

  boolean canChangeFileSilently(PsiFileSystemItem file) {
    if (cutOperationJustHappened) return false;
    VirtualFile virtualFile = file.getVirtualFile();
    if (virtualFile == null) return false;
    if (file instanceof PsiCodeFragment) return true;
    Project project = file.getProject();
    if (!ModuleUtil.projectContainsFile(project, virtualFile, false)) return false;
    AbstractVcs activeVcs = ProjectLevelVcsManager.getInstance(project).getVcsFor(virtualFile);
    if (activeVcs == null) return true;
    FileStatus status = FileStatusManager.getInstance(project).getStatus(virtualFile);

    if (status == FileStatus.MODIFIED || status == FileStatus.ADDED) return true;
    FileEditor[] editors = FileEditorManager.getInstance(myProject).getEditors(virtualFile);
    for (FileEditor editor : editors) {
      if (editor.isModified()) return true;
    }
    return false;
  }

  private class MyApplicationListener extends ApplicationAdapter {
    public void beforeWriteActionStart(Object action) {
      if (myDaemonCodeAnalyzer.getUpdateProgress().isCanceled()) return;
      if (LOG.isDebugEnabled()) {
        LOG.debug("cancelling code highlighting by write action:" + action);
      }
      if (action instanceof DocumentRunnable) {
        Document document = ((DocumentRunnable)action).getDocument();
        if (!worthBothering(document, ((DocumentRunnable)action).getProject())) {
          return;
        }
      }
      stopDaemon(false);
    }

    public void writeActionFinished(Object action) {
      if (myDaemonCodeAnalyzer.getUpdateProgress().isRunning()) {
        return;
      }
      if (action instanceof DocumentRunnable) {
        Document document = ((DocumentRunnable)action).getDocument();
        if (!worthBothering(document, ((DocumentRunnable)action).getProject())) {
          return;
        }
      }
      stopDaemon(true);
    }
  }

  private class MyCommandListener extends CommandAdapter {
    private final Object myCutActionName =
      ActionManager.getInstance().getAction(IdeActions.ACTION_EDITOR_CUT).getTemplatePresentation().getText();

    public void commandStarted(CommandEvent event) {
      Document affectedDocument = extractDocumentFromCommand(event);
      if (!worthBothering(affectedDocument, event.getProject())) {
        return;
      }

      cutOperationJustHappened = myCutActionName.equals(event.getCommandName());
      if (myDaemonCodeAnalyzer.getUpdateProgress().isCanceled()) return;
      if (LOG.isDebugEnabled()) {
        LOG.debug("cancelling code highlighting by command:" + event.getCommand());
      }
      stopDaemon(false);
    }

    private Document extractDocumentFromCommand(CommandEvent event) {
      Document affectedDocument = event.getDocument();
      if (affectedDocument != null) return affectedDocument;
      Object id = event.getCommandGroupId();

      if (id instanceof Document) {
        affectedDocument = (Document)id;
      }
      else if (id instanceof Ref && ((Ref)id).get() instanceof Document) {
        affectedDocument = (Document)((Ref)id).get();
      }
      return affectedDocument;
    }

    public void commandFinished(CommandEvent event) {
      Document affectedDocument = extractDocumentFromCommand(event);
      if (!worthBothering(affectedDocument, event.getProject())) {
        return;
      }

      if (myEscPressed) {
        myEscPressed = false;
        if (affectedDocument != null) {
          // prevent Esc key to leave the document in the not-highlighted state
          if (!myDaemonCodeAnalyzer.getFileStatusMap().allDirtyScopesAreNull(affectedDocument)) {
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

    public void profileActivated(Profile oldProfile, Profile profile) {
      myDaemonCodeAnalyzer.restart();
    }
  }

  private class MyAnActionListener implements AnActionListener {
    private final AnAction escapeAction = ActionManager.getInstance().getAction(IdeActions.ACTION_EDITOR_ESCAPE);

    public void beforeActionPerformed(AnAction action, DataContext dataContext, AnActionEvent event) {
      myEscPressed = action == escapeAction;
    }

    public void afterActionPerformed(final AnAction action, final DataContext dataContext, AnActionEvent event) {
    }

    public void beforeEditorTyping(char c, DataContext dataContext) {
      Editor editor = PlatformDataKeys.EDITOR.getData(dataContext);
      //no need to stop daemon if something happened in the console
      if (editor != null && !worthBothering(editor.getDocument(), editor.getProject())) {
        return;
      }
      stopDaemon(true);
    }
  }

  private static class MyEditorMouseListener extends EditorMouseAdapter {

    public void mouseExited(EditorMouseEvent e) {
      if (!TooltipController.getInstance().shouldSurvive(e.getMouseEvent())) {
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
        if (!shown && !TooltipController.getInstance().shouldSurvive(e.getMouseEvent())) {
          DaemonTooltipUtil.cancelTooltips();
        }
      }
    }

    public void mouseDragged(EditorMouseEvent e) {
      TooltipController.getInstance().cancelTooltips();
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
