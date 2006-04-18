/*
 * @author: Eugene Zhuravlev
 * Date: Jan 22, 2003
 * Time: 2:25:31 PM
 */
package com.intellij.compiler.progress;

import com.intellij.compiler.CompilerMessageImpl;
import com.intellij.compiler.CompilerWorkspaceConfiguration;
import com.intellij.compiler.impl.CompilerErrorTreeView;
import com.intellij.ide.errorTreeView.NewErrorTreeViewPanel;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.compiler.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.util.ProgressIndicatorBase;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManagerListener;
import com.intellij.openapi.project.ex.ProjectEx;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import com.intellij.ui.content.ContentManagerAdapter;
import com.intellij.ui.content.ContentManagerEvent;
import com.intellij.util.Alarm;
import com.intellij.util.ui.MessageCategory;
import com.intellij.codeInsight.problems.ProblemsToolWindow;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.StringTokenizer;

public class CompilerProgressIndicator extends ProgressIndicatorBase {
  private static final Logger LOG = Logger.getInstance("#com.intellij.compiler.progress.CompilerProgressIndicator");
  private static final boolean IS_UNIT_TEST_MODE = ApplicationManager.getApplication().isUnitTestMode();
  private static final int UPDATE_INTERVAL = 50; //msec. 20 frames per second.
  private CompilerProgressDialog myDialog;
  private final Project myProject;
  private boolean myIsBackgroundMode;
  private int myErrorCount = 0;
  private int myWarningCount = 0;
  private String myStatisticsText = "";
  private final Alarm myAlarm = new Alarm(Alarm.ThreadToUse.SHARED_THREAD);
  private final ProblemsToolWindow myProblemsToolWindow;
  private final Object myMessageViewLock = new Object();

  public CompilerProgressIndicator(Project project, boolean compileInBackground, String contentName) {
    myProject = project;
    myIsBackgroundMode = compileInBackground;
    myProblemsToolWindow = ProblemsToolWindow.getInstance(myProject);
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

  public void addMessage(final CompilerMessage message) {
    if (CompilerMessageCategory.ERROR.equals(message.getCategory())) {
      myErrorCount += 1;
      openMessageView();                                      
    }
    if (CompilerMessageCategory.WARNING.equals(message.getCategory())) {
      myWarningCount += 1;
    }
    if (ApplicationManager.getApplication().isDispatchThread()) {
      doAddMessage(message);
    }
    else {
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        public void run() {
          doAddMessage(message);
        }
      }, getMyModalityState());
    }
  }

  private void doAddMessage(final CompilerMessage message) {
    if (IS_UNIT_TEST_MODE || isCanceled()) {
      return;
    }

    final Navigatable navigatable = message.getNavigatable();
    final VirtualFile file = message.getVirtualFile();
    final int type = translateCategory(message.getCategory());
    final String[] text = convertMessage(message);
    ProblemsToolWindow problemsToolWindow = myProblemsToolWindow;
    if (navigatable == null) {
      problemsToolWindow.addMessage(type, text, file, -1, -1, null);
    }
    else {
      final String groupName = file == null ? message.getCategory().getPresentableText() : file.getPresentableUrl();
      problemsToolWindow.addMessage(type, text, groupName, navigatable, message.getExportTextPrefix(), message.getRenderTextPrefix(), file);
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

  private static int translateCategory(CompilerMessageCategory category) {
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
    CompilerErrorTreeView errorTreeView = new CompilerErrorTreeView(myProject);
    errorTreeView.setProcessController(new NewErrorTreeViewPanel.ProcessController() {
      public void stopProcess() {
        cancel();
      }

      public boolean isProcessStopped() {
        return !isRunning();
      }
    });
    myProblemsToolWindow.initErrorTreeView(errorTreeView, new CloseListener());

    if (myIsBackgroundMode) {
      openMessageView();
    }
    else {
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        public void run() {
          if (isRunning()) {
            synchronized (myMessageViewLock) {
              // clear messages from the previous compilation
              myProblemsToolWindow.clearAllContents();
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
            myProblemsToolWindow.setProgressText(s);
            myProblemsToolWindow.setProgressStatistics(myStatisticsText);
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
    myProblemsToolWindow.showToolWindow(true);

    myProblemsToolWindow.setProgressText(myDialog.getStatusText());
    myProblemsToolWindow.setProgressStatistics(myDialog.getStatistics());

    SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        closeProgressDialog();
      }
    });
  }

  private void openMessageView() {
    if (IS_UNIT_TEST_MODE || isCanceled()) {
      return;
    }
    // the work with ToolWindowManager should be done in the Swing thread
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      public void run() {
        myProblemsToolWindow.showToolWindow(CompilerWorkspaceConfiguration.getInstance(myProject).COMPILE_IN_BACKGROUND);
        updateProgressText();
      }
    }, getMyModalityState());
  }

  private ModalityState getMyModalityState() {
    final Window window = getWindow();
    return window == null ? ModalityState.NON_MMODAL : ModalityState.stateForComponent(window);
  }

  public void showCompilerContent() {
    myProblemsToolWindow.showToolWindow(false);
  }

  private void closeUI() {
    if (IS_UNIT_TEST_MODE) {
      return;
    }
    final Application application = ApplicationManager.getApplication();
    application.invokeLater(new Runnable() {
      public void run() {
        closeProgressDialog();
        final boolean closeViewOnSuccess = CompilerWorkspaceConfiguration.getInstance(myProject).CLOSE_MESSAGE_VIEW_IF_SUCCESS;
        synchronized (myMessageViewLock) {
          final boolean hasMessagesToRead = myErrorCount > 0 || myWarningCount > 0;
          final boolean shouldRetainView = hasMessagesToRead || !closeViewOnSuccess;
          if (shouldRetainView) {
            addMessage(
              new CompilerMessageImpl(myProject, CompilerMessageCategory.STATISTICS,
                                      CompilerBundle.message("statistics.error.count", myErrorCount), null, -1, -1, null));
            addMessage(
              new CompilerMessageImpl(myProject, CompilerMessageCategory.STATISTICS,
                                      CompilerBundle.message("statistics.warnings.count", myWarningCount), null, -1, -1, null));
            myProblemsToolWindow.showToolWindow(true);
            myProblemsToolWindow.selectFirstMessage();
          }
          else {
            //removeAllContents(myProject, null);
          }
        }
      }
    }, getMyModalityState());
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
    private boolean myIsApplicationExitingOrProjectClosing = false;
    private boolean myUserAcceptedCancel = false;

    public CloseListener() {
      ProjectManagerEx.getInstanceEx().addProjectManagerListener(myProject, this);
    }

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
              ((ProjectEx)project).dispose();
          }
        });
        cancel();
        return false; // cancel compiler and let it finish, after compilation close the project, but currently - veto closing
      }
      return !isRunning();
    }

    public void contentRemoved(ContentManagerEvent event) {
      synchronized (myMessageViewLock) {
        if (isRunning()) {
          cancel();
        }
      }
    }

    public void contentRemoveQuery(ContentManagerEvent event) {
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

    private boolean shouldAskUser() {
      if (myUserAcceptedCancel) {
        return false; // do not ask second time if user already accepted closing
      }
      return !myIsApplicationExitingOrProjectClosing && isRunning();
    }

    public void projectOpened(Project project) {
    }

    public void projectClosed(Project project) {
    }

    public void projectClosing(Project project) {
      myIsApplicationExitingOrProjectClosing = true;
      ProjectManagerEx.getInstanceEx().removeProjectManagerListener(myProject, this);
    }
  }

}
