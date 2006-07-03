/*
 * @author: Eugene Zhuravlev
 * Date: Jan 22, 2003
 * Time: 2:25:31 PM
 */
package com.intellij.compiler.progress;

import com.intellij.compiler.CompilerMessageImpl;
import com.intellij.compiler.CompilerWorkspaceConfiguration;
import com.intellij.compiler.impl.CompilerErrorTreeView;
import com.intellij.compiler.impl.CompileDriver;
import com.intellij.ide.errorTreeView.NewErrorTreeViewPanel;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.compiler.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.util.ProgressIndicatorBase;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManagerListener;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.editor.Document;
import com.intellij.peer.PeerFactory;
import com.intellij.pom.Navigatable;
import com.intellij.problems.WolfTheProblemSolver;
import com.intellij.problems.Problem;
import com.intellij.ui.content.*;
import com.intellij.util.Alarm;
import com.intellij.util.ui.MessageCategory;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.StringTokenizer;

public class CompilerProgressIndicator extends ProgressIndicatorBase {
  private static final Logger LOG = Logger.getInstance("#com.intellij.compiler.progress.CompilerProgressIndicator");
  private static final boolean IS_UNIT_TEST_MODE = ApplicationManager.getApplication().isUnitTestMode();
  private static final int UPDATE_INTERVAL = 50; //msec. 20 frames per second.
  private static final Key<Key<?>> CONTENT_ID_KEY = Key.create("CONTENT_ID");
  private final Key<Key<?>> myContentId = Key.create("compile_content");
  private CompilerProgressDialog myDialog;
  private NewErrorTreeViewPanel myErrorTreeView;
  private final Object myMessageViewLock = new Object();
  private final Project myProject;
  private final String myContentName;
  private boolean myIsBackgroundMode;
  private int myErrorCount = 0;
  private int myWarningCount = 0;
  private String myStatisticsText = "";
  private final Alarm myAlarm = new Alarm(Alarm.ThreadToUse.SHARED_THREAD);

  public CompilerProgressIndicator(@NotNull Project project, boolean compileInBackground, String contentName) {
    myProject = project;
    myIsBackgroundMode = compileInBackground;
    myContentName = contentName;
  }

  public void cancel() {
    if (!isCanceled()) {
      super.cancel();
      closeUI();
    }
  }

  public void setText(String text) {
    super.setText(text);
    updateProgressText();
  }

  public void setText2(String text) {
    myStatisticsText = text;
    updateProgressText();
  }

  public void setFraction(double fraction) {
    super.setFraction(fraction);
    updateProgressText();
  }

  public void addMessage(final CompileContext compileContext, final CompilerMessage message) {
    openMessageView();
    if (CompilerMessageCategory.ERROR.equals(message.getCategory())) {
      myErrorCount += 1;
      informWolf(message, compileContext);
    }
    if (CompilerMessageCategory.WARNING.equals(message.getCategory())) {
      myWarningCount += 1;
    }

    if (ApplicationManager.getApplication().isDispatchThread()) {
      doAddMessage(message);
    }
    else {
      final Window window = getWindow();
      final ModalityState modalityState = window != null ? ModalityState.stateForComponent(window) : ModalityState.NON_MMODAL;
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        public void run() {
          doAddMessage(message);
        }
      }, modalityState);
    }
  }

  private void informWolf(final CompilerMessage message, final CompileContext compileContext) {
    WolfTheProblemSolver wolf = WolfTheProblemSolver.getInstance(myProject);
    Problem problem = wolf.convertToProblem(message);
    if (problem != null && problem.getVirtualFile() != null) {
      VirtualFile virtualFile = problem.getVirtualFile();
      Document document = FileDocumentManager.getInstance().getDocument(virtualFile);

      Long compileStart = compileContext.getUserData(CompileDriver.COMPILATION_START_TIMESTAMP);
      if (document != null && compileStart != null && compileStart.longValue() > document.getModificationStamp()) {
        // user might have changed the file after compile start
        wolf.weHaveGotProblem(problem);
      }
    }
  }

  private void doAddMessage(final CompilerMessage message) {
    synchronized (myMessageViewLock) {
      if (myErrorTreeView != null) {
        final Navigatable navigatable = message.getNavigatable();
        final VirtualFile file = message.getVirtualFile();
        final int type = translateCategory(message.getCategory());
        final String[] text = convertMessage(message);
        if (navigatable != null) {
          final String groupName = file != null? file.getPresentableUrl() : message.getCategory().getPresentableText();
          myErrorTreeView.addMessage(type, text, groupName, navigatable, message.getExportTextPrefix(), message.getRenderTextPrefix(), null);
        }
        else {
          myErrorTreeView.addMessage(type, text, file, -1, -1, null);
        }
      }
    }
  }

  private static String[] convertMessage(final CompilerMessage message) {
    String text = message.getMessage();
    if (!text.contains("\n")) {
      return new String[]{text};
    }
    ArrayList<String> lines = new ArrayList<String>();
    StringTokenizer tokenizer = new StringTokenizer(text, "\n", false);
    while (tokenizer.hasMoreTokens()) {
      lines.add(tokenizer.nextToken());
    }
    return lines.toArray(new String[lines.size()]);
  }

  public static int translateCategory(CompilerMessageCategory category) {
    if (CompilerMessageCategory.ERROR.equals(category)) {
      return MessageCategory.ERROR;
    }
    if (CompilerMessageCategory.WARNING.equals(category)) {
      return MessageCategory.WARNING;
    }
    if (CompilerMessageCategory.STATISTICS.equals(category)) {
      return MessageCategory.STATISTICS;
    }
    if (CompilerMessageCategory.INFORMATION.equals(category)) {
      return MessageCategory.INFORMATION;
    }
    LOG.error("Unknown message category: " + category);
    return 0;
  }

  public void start() {
    super.start();
    if (IS_UNIT_TEST_MODE) {
      return;
    }
    if (myIsBackgroundMode) {
      openMessageView();
    }
    else {
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        public void run() {
          if (isRunning()) {
            synchronized (myMessageViewLock) {
              // clear messages from the previous compilation
              if (myErrorTreeView == null) {
                // if message view != null, the contents has already been cleared
                removeAllContents(myProject, null);
              }
            }
            final CompilerProgressDialog dialog = openProgressDialog();
            dialog.show();
          }
        }
      }, ModalityState.NON_MMODAL);
    }
  }

  public void stop() {
    super.stop();
    if (!isCanceled()) { // when cancelled the UI is already closed
      closeUI();
    }
  }

  private void updateProgressText() {
    if (IS_UNIT_TEST_MODE) {
      return;
    }
    if (myAlarm.getActiveRequestCount() == 0) {
      myAlarm.addRequest(myRepaintRequest, UPDATE_INTERVAL);
    }
  }

  private Runnable myRepaintRequest = new Runnable() {
    public void run() {
      SwingUtilities.invokeLater(myRepaintRunnable);
    }
    private Runnable myRepaintRunnable = new Runnable() {
      public void run() {
        String s = getText();
        if (getFraction() > 0) {
          s += " " + (int)(getFraction() * 100 + 0.5) + "%";
        }

        synchronized (myMessageViewLock) {
          if (myIsBackgroundMode) {
            if (myErrorTreeView != null) {
              myErrorTreeView.setProgressText(s);
              myErrorTreeView.setProgressStatistics(myStatisticsText);
            }
          }
          else {
            if (myDialog != null) {
              myDialog.setStatusText(s);
              myDialog.setStatisticsText(myStatisticsText);
            }
          }
        }
      }
    };
  };

  public void sendToBackground() {
    myIsBackgroundMode = true;
    openMessageView();
    activateMessageView();
    synchronized (myMessageViewLock) {
      if (myErrorTreeView != null) { // when cancelled, openMessageView() may not create the view
        myErrorTreeView.setProgressText(myDialog.getStatusText());
        myErrorTreeView.setProgressStatistics(myDialog.getStatistics());
      }
    }
    SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        closeProgressDialog();
      }
    });
  }

  // error tree view handling:

  private void openMessageView() {
    if (IS_UNIT_TEST_MODE) {
      return;
    }
    if (isCanceled()) {
      return;
    }
    synchronized (myMessageViewLock) {
      if (myErrorTreeView != null) {
        return;
      }
      myErrorTreeView = new CompilerErrorTreeView(myProject);
      myErrorTreeView.setProcessController(new NewErrorTreeViewPanel.ProcessController() {
        public void stopProcess() {
          cancel();
        }

        public boolean isProcessStopped() {
          return !isRunning();
        }
      });
    }
    final Window window = getWindow();
    final ModalityState modalityState = window != null ? ModalityState.stateForComponent(window) : ModalityState.NON_MMODAL;
    // the work with ToolWindowManager should be done in the Swing thread
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      public void run() {
        final MessageView messageView = myProject.getComponent(MessageView.class);
        final JComponent component;
        synchronized (myMessageViewLock) {
          component = myErrorTreeView.getComponent();
        }
        final Content content = PeerFactory.getInstance().getContentFactory().createContent(component, myContentName, true);
        content.putUserData(CONTENT_ID_KEY, myContentId);
        messageView.addContent(content);
        new CloseListener(content, messageView);
        removeAllContents(myProject, content);
        messageView.setSelectedContent(content);
        ToolWindow toolWindow = ToolWindowManager.getInstance(myProject).getToolWindow(ToolWindowId.MESSAGES_WINDOW);
        if (toolWindow != null) {
          if (CompilerWorkspaceConfiguration.getInstance(myProject).COMPILE_IN_BACKGROUND) {
            toolWindow.activate(null);
          }
          else {
            toolWindow.show(null);
          }
        }
        updateProgressText();
      }
    }, modalityState);
  }

  public void showCompilerContent() {
    synchronized (myMessageViewLock) {
      if (myErrorTreeView != null) {
        final MessageView messageView = myProject.getComponent(MessageView.class);
        Content[] contents = messageView.getContents();
        for (Content content : contents) {
          if (content.getUserData(CONTENT_ID_KEY) != null) {
            messageView.setSelectedContent(content);
            return;
          }
        }
      }
    }
  }

  public static void removeAllContents(Project project, Content notRemove) {
    MessageView messageView = project.getComponent(MessageView.class);
    Content[] contents = messageView.getContents();
    for (Content content : contents) {
      if (content.isPinned()) continue;
      if (content == notRemove) continue;
      if (content.getUserData(CONTENT_ID_KEY) != null) { // the content was added by me
        messageView.removeContent(content);
      }
    }
  }

  private void activateMessageView() {
    synchronized (myMessageViewLock) {
      if (myErrorTreeView != null) {
        ToolWindowManager.getInstance(myProject).getToolWindow(ToolWindowId.MESSAGES_WINDOW).activate(null);
      }
    }
  }

  private void closeUI() {
    if (IS_UNIT_TEST_MODE) {
      return;
    }
    Window window = getWindow();
    ModalityState modalityState = window != null ? ModalityState.stateForComponent(window) : ModalityState.NON_MMODAL;
    final Application application = ApplicationManager.getApplication();
    application.invokeLater(new Runnable() {
      public void run() {
        closeProgressDialog();
        final boolean closeViewOnSuccess = CompilerWorkspaceConfiguration.getInstance(myProject).CLOSE_MESSAGE_VIEW_IF_SUCCESS;
        synchronized (myMessageViewLock) {
          if (myErrorTreeView != null) {
            final boolean hasMessagesToRead = myErrorCount > 0 || myWarningCount > 0;
            final boolean shouldRetainView = hasMessagesToRead || !closeViewOnSuccess;
            if (shouldRetainView) {
              addMessage(null, new CompilerMessageImpl(myProject, CompilerMessageCategory.STATISTICS,
                                        CompilerBundle.message("statistics.error.count", myErrorCount), null, -1, -1, null));
              addMessage(null, new CompilerMessageImpl(myProject, CompilerMessageCategory.STATISTICS,
                                        CompilerBundle.message("statistics.warnings.count", myWarningCount), null, -1, -1, null));
              activateMessageView();
              myErrorTreeView.selectFirstMessage();
            }
            else {
              removeAllContents(myProject, null);
            }
          }
        }
      }
    }, modalityState);
  }

  public Window getWindow(){
    if (!myIsBackgroundMode && myDialog != null) {
      return SwingUtilities.windowForComponent(myDialog.getContentPane());
    }
    else{
      return null;
    }
  }

  private CompilerProgressDialog openProgressDialog() {
    synchronized (myMessageViewLock) {
      if (myDialog == null) {
        myDialog = new CompilerProgressDialog(this, myProject);
      }
      return myDialog;
    }
  }

  private void closeProgressDialog() {
    synchronized (myMessageViewLock) {
      if (myDialog != null) {
        myDialog.close(DialogWrapper.CANCEL_EXIT_CODE);
        myDialog = null;
      }
    }
  }

  private class CloseListener extends ContentManagerAdapter implements ProjectManagerListener {
    private Content myContent;
    private ContentManager myContentManager;
    private boolean myIsApplicationExitingOrProjectClosing = false;
    private boolean myUserAcceptedCancel = false;

    public boolean canCloseProject(final Project project) {
      if (shouldAskUser()) {
        int result = Messages.showOkCancelDialog(
          myProject,
          CompilerBundle.message("warning.compiler.running.on.project.close"),
          CompilerBundle.message("compiler.running.dialog.title"),
          Messages.getQuestionIcon()
        );
        if (result != 0) {
          return false; // veto closing
        }
        myUserAcceptedCancel = true;

        final CompilerManager compilerManager = CompilerManager.getInstance(project);
        compilerManager.addCompilationStatusListener(new CompilationStatusListener() {
          public void compilationFinished(boolean aborted, int errors, int warnings, final CompileContext compileContext) {
            compilerManager.removeCompilationStatusListener(this);
            if (ProjectManagerEx.getInstanceEx().closeProject(project))
              Disposer.dispose(project);
          }
        });
        cancel();
        return false; // cancel compiler and let it finish, after compilation close the project, but currently - veto closing
      }
      return !isRunning();
    }

    public CloseListener(Content content, ContentManager contentManager) {
      myContent = content;
      myContentManager = contentManager;
      contentManager.addContentManagerListener(this);
      ProjectManagerEx.getInstanceEx().addProjectManagerListener(myProject, this);
    }

    public void contentRemoved(ContentManagerEvent event) {
      if (event.getContent() == myContent) {
        synchronized (myMessageViewLock) {
          if (myErrorTreeView != null) {
            myErrorTreeView.dispose();
            myErrorTreeView = null;
            if (isRunning()) {
              cancel();
            }
          }
        }
        myContentManager.removeContentManagerListener(this);
        myContent.release();
        myContent = null;
      }
    }

    public void contentRemoveQuery(ContentManagerEvent event) {
      if (event.getContent() == myContent) {
        if (!isCanceled() && shouldAskUser()) {
          int result = Messages.showOkCancelDialog(
            myProject,
            CompilerBundle.message("warning.compiler.running.on.toolwindow.close"),
            CompilerBundle.message("compiler.running.dialog.title"),
            Messages.getQuestionIcon()
          );
          if (result != 0) {
            event.consume(); // veto closing
          }
          myUserAcceptedCancel = true;
        }
      }
    }

    private boolean shouldAskUser() {
      if (myUserAcceptedCancel) {
        return false; // do not ask second time if user already accepted closing
      }
      return !myIsApplicationExitingOrProjectClosing && isRunning();
    }

    public void projectOpened(Project project) {
    }

    public void projectClosed(Project project) {
      if (myContent != null) {
        myContentManager.removeContent(myContent);
      }
    }

    public void projectClosing(Project project) {
      myIsApplicationExitingOrProjectClosing = true;
      ProjectManagerEx.getInstanceEx().removeProjectManagerListener(myProject, this);
    }
  }
}
