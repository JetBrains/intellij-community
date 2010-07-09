/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

package com.intellij.execution;

import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.ide.errorTreeView.NewErrorTreeViewPanel;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.ui.content.MessageView;
import com.intellij.util.Function;
import com.intellij.util.NotNullFunction;
import com.intellij.util.concurrency.Semaphore;
import com.intellij.util.ui.ErrorTreeView;
import com.intellij.util.ui.MessageCategory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Created by IntelliJ IDEA.
 *
 * @author: Roman Chernyatchik
 * @date: Oct 4, 2007
 */
public class ExecutionHelper {
  private static final Logger LOG = Logger.getInstance(ExecutionHelper.class.getName());

  private ExecutionHelper() {
  }

  public static void showErrors(@NotNull final Project myProject,
                                @NotNull final List<Exception> exceptionList,
                                @NotNull final String tabDisplayName,
                                @Nullable final VirtualFile file) {
    if (ApplicationManager.getApplication().isUnitTestMode() && !exceptionList.isEmpty()) {
      throw new RuntimeException(exceptionList.get(0));
    }
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      public void run() {
        if (myProject.isDisposed()) return;
        if (exceptionList.isEmpty()) {
          removeContents(null, myProject, tabDisplayName);
          return;
        }

        final RailsErrorViewPanel errorTreeView = new RailsErrorViewPanel(myProject);
        try {
          openMessagesView(errorTreeView, myProject, tabDisplayName);
        }
        catch (NullPointerException e) {
          final StringBuilder builder = new StringBuilder();
          builder.append("Exceptions occured:");
          for (final Exception exception : exceptionList) {
            builder.append("\n");
            builder.append(exception.getMessage());
          }
          Messages.showErrorDialog(builder.toString(), "Execution Error");
          return;
        }
        for (final Exception exception : exceptionList) {
          String[] messages = StringUtil.splitByLines(exception.getMessage());
          if (messages.length == 0) messages = new String[]{"Unknown Error"};
          errorTreeView.addMessage(MessageCategory.ERROR, messages, file, -1, -1, null);
        }

        ToolWindowManager.getInstance(myProject).getToolWindow(ToolWindowId.MESSAGES_WINDOW).activate(null);
      }
    });
  }

  private static void openMessagesView(@NotNull final RailsErrorViewPanel errorTreeView,
                                       @NotNull final Project myProject,
                                       @NotNull final String tabDisplayName) {
    CommandProcessor commandProcessor = CommandProcessor.getInstance();
    commandProcessor.executeCommand(myProject, new Runnable() {
      public void run() {
        final MessageView messageView = ServiceManager.getService(myProject, MessageView.class);
        final Content content = ContentFactory.SERVICE.getInstance().createContent(errorTreeView, tabDisplayName, true);
        messageView.getContentManager().addContent(content);
        Disposer.register(content, errorTreeView);
        messageView.getContentManager().setSelectedContent(content);
        removeContents(content, myProject, tabDisplayName);
      }
    }, "Open message view", null);
  }

  private static void removeContents(@Nullable final Content notToRemove,
                                     @NotNull final Project myProject,
                                     @NotNull final String tabDisplayName) {
    MessageView messageView = ServiceManager.getService(myProject, MessageView.class);
    Content[] contents = messageView.getContentManager().getContents();
    for (Content content : contents) {
      LOG.assertTrue(content != null);
      if (content.isPinned()) continue;
      if (tabDisplayName.equals(content.getDisplayName()) && content != notToRemove) {
        ErrorTreeView listErrorView = (ErrorTreeView)content.getComponent();
        if (listErrorView != null) {
          if (messageView.getContentManager().removeContent(content, true)) {
            content.release();
          }
        }
      }
    }
  }

  @Nullable
  public static ProcessHandler findRunningConsole(final Project project,
                                                  @NotNull final NotNullFunction<String, Boolean> cmdLineMatcher) {
    final ProcessHandler[] processes = ExecutionManager.getInstance(project).getRunningProcesses();
    for (ProcessHandler process : processes) {
      if (process instanceof OSProcessHandler && !process.isProcessTerminated()) {
        final String commandLine = ((OSProcessHandler)process).getCommandLine();
        if (cmdLineMatcher.fun(commandLine).booleanValue()) {
          return process;
        }
      }
    }
    return null;
  }

  public static class RailsErrorViewPanel extends NewErrorTreeViewPanel {
    public RailsErrorViewPanel(final Project project) {
      super(project, "reference.toolWindows.messages");
    }

    protected boolean canHideWarnings() {
      return false;
    }
  }


  public static void executeExternalProcess(@Nullable final Project myProject,
                                            @NotNull final OSProcessHandler processHandler,
                                            @NotNull final ExecutionMode mode) {
    final String title = mode.getTitle() != null ? mode.getTitle() : "Running. Please wait...";
    assert title != null;

    final Runnable process;
    if (mode.cancelable()) {
      process = createCancelableExecutionProcess(processHandler, mode.shouldCancelFun());
    }
    else {
      if (mode.getTimeout() <= 0) {
        process = new Runnable() {
          public void run() {
            processHandler.waitFor();
          }
        };
      } else {
        process = createTimelimitedExecutionProcess(processHandler, mode.getTimeout());
      }
    }
    if (mode.withModalProgress()) {
      ProgressManager.getInstance().runProcessWithProgressSynchronously(process, title, mode.cancelable(), myProject,
                                                                        mode.getProgressParentComponent());
    }
    else if (mode.inBackGround()) {
      final Task task = new Task.Backgroundable(myProject, title, mode.cancelable()) {
        public void run(@NotNull final ProgressIndicator indicator) {
          process.run();
        }
      };
      ProgressManager.getInstance().run(task);
    }
    else {
      final String title2 = mode.getTitle2();
      final ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
      if (indicator != null && title2 != null) {
        indicator.setText2(title2);
      }
      process.run();
    }
  }

  private static Runnable createCancelableExecutionProcess(final ProcessHandler processHandler,
                                                           final Function<Object, Boolean> cancelableFun) {
    return new Runnable() {
      private ProgressIndicator myProgressIndicator;
      private final Semaphore mySemaphore = new Semaphore();

      private final Runnable myWaitThread = new Runnable() {
        public void run() {
          try {
            processHandler.waitFor();
          }
          finally {
            mySemaphore.up();
          }
        }
      };

      private final Runnable myCancelListener = new Runnable() {
        public void run() {
          for (; ;) {
            if ((myProgressIndicator != null && (myProgressIndicator.isCanceled()
                                                 || !myProgressIndicator.isRunning()))
                || (cancelableFun != null && cancelableFun.fun(null).booleanValue())
                || processHandler.isProcessTerminated()) {

              if (!processHandler.isProcessTerminated()) {
                try {
                  processHandler.destroyProcess();
                }
                finally {
                  mySemaphore.up();
                }
              }
              break;
            }
            try {
              synchronized (this) {
                wait(1000);
              }
            }
            catch (InterruptedException e) {
              //Do nothing
            }
          }
        }
      };

      public void run() {
        myProgressIndicator = ProgressManager.getInstance().getProgressIndicator();
        if (myProgressIndicator != null && StringUtil.isEmpty(myProgressIndicator.getText())) {
          myProgressIndicator.setText("Please wait");
        }

        LOG.assertTrue(myProgressIndicator != null || cancelableFun != null,
                       "Cancelable process must have an opportunity to be canceled!");
        mySemaphore.down();
        ApplicationManager.getApplication().executeOnPooledThread(myWaitThread);
        ApplicationManager.getApplication().executeOnPooledThread(myCancelListener);

        mySemaphore.waitFor();
      }
    };
  }

  private static Runnable createTimelimitedExecutionProcess(final OSProcessHandler processHandler,
                                                            final int timeout) {
    return new Runnable() {
      private final Semaphore mySemaphore = new Semaphore();

      private final Runnable myProcessThread = new Runnable() {
        public void run() {
          try {
            final boolean finished = processHandler.waitFor(1000 * timeout);
            if (!finished) {
              LOG.error("Timeout (" + timeout + " sec) on executing: " + processHandler.getCommandLine());
              processHandler.destroyProcess();
            }
          } finally {
            mySemaphore.up();
          }
        }
      };

      public void run() {
        mySemaphore.down();
        ApplicationManager.getApplication().executeOnPooledThread(myProcessThread);

        mySemaphore.waitFor();
      }
    };
  }
}
