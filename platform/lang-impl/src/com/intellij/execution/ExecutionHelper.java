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

import com.google.common.collect.Lists;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.execution.ui.RunContentManagerImpl;
import com.intellij.ide.errorTreeView.NewErrorTreeViewPanel;
import com.intellij.ide.ui.ListCellRendererWrapper;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.popup.PopupChooserBuilder;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.components.JBList;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.ui.content.MessageView;
import com.intellij.util.Consumer;
import com.intellij.util.Function;
import com.intellij.util.NotNullFunction;
import com.intellij.util.concurrency.Semaphore;
import com.intellij.util.ui.ErrorTreeView;
import com.intellij.util.ui.MessageCategory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
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

  public static Collection<RunContentDescriptor> findRunningConsoleByCmdLine(final Project project,
                                                                             @NotNull final NotNullFunction<String, Boolean> cmdLineMatcher) {
    return findRunningConsole(project, new NotNullFunction<RunContentDescriptor, Boolean>() {
      @NotNull
      @Override
      public Boolean fun(RunContentDescriptor selectedContent) {
        final ProcessHandler processHandler = selectedContent.getProcessHandler();
        if (processHandler instanceof OSProcessHandler && !processHandler.isProcessTerminated()) {
          final String commandLine = ((OSProcessHandler)processHandler).getCommandLine();
          return cmdLineMatcher.fun(commandLine);
        }
        return false;
      }
    });
  }

  public static Collection<RunContentDescriptor> findRunningConsoleByTitle(final Project project,
                                                                           @NotNull final NotNullFunction<String, Boolean> titleMatcher) {
    return findRunningConsole(project, new NotNullFunction<RunContentDescriptor, Boolean>() {
      @NotNull
      @Override
      public Boolean fun(RunContentDescriptor selectedContent) {
        return titleMatcher.fun(selectedContent.getDisplayName());
      }
    });
  }

  public static Collection<RunContentDescriptor> findRunningConsole(final Project project,
                                                                    @NotNull final NotNullFunction<RunContentDescriptor, Boolean> descriptorMatcher) {
    final ExecutionManager executionManager = ExecutionManager.getInstance(project);
    final ToolWindow runToolWindow =
      ToolWindowManager.getInstance(project).getToolWindow(DefaultRunExecutor.getRunExecutorInstance().getId());
    if (runToolWindow != null && runToolWindow.isVisible()) {
      final RunContentDescriptor selectedContent = executionManager.getContentManager().getSelectedContent();
      if (selectedContent != null) {
        if (descriptorMatcher.fun(selectedContent)) {
          return Collections.singletonList(selectedContent);
        }
      }
    }

    final ArrayList<RunContentDescriptor> result = Lists.newArrayList();
    final RunContentDescriptor[] runContentDescriptors = ((RunContentManagerImpl)executionManager.getContentManager()).getAllDescriptors();
    for (RunContentDescriptor runContentDescriptor : runContentDescriptors) {
      if (descriptorMatcher.fun(runContentDescriptor)) {
        result.add(runContentDescriptor);
      }
    }
    return result;
  }

  public static List<RunContentDescriptor> collectConsolesByDisplayName(final Project project,
                                                                        @NotNull NotNullFunction<String, Boolean> titleMatcher) {
    List<RunContentDescriptor> result = Lists.newArrayList();
    final ExecutionManager executionManager = ExecutionManager.getInstance(project);
    final RunContentDescriptor[] runContentDescriptors = ((RunContentManagerImpl)executionManager.getContentManager()).getAllDescriptors();
    for (RunContentDescriptor runContentDescriptor : runContentDescriptors) {
      if (titleMatcher.fun(runContentDescriptor.getDisplayName())) {
        result.add(runContentDescriptor);
      }
    }
    return result;
  }

  public static void selectContentDescriptor(final @NotNull Editor editor,
                                                             @NotNull Collection<RunContentDescriptor> consoles,
                                                             String selectDialogTitle, final Consumer<RunContentDescriptor> descriptorConsumer) {
    if (consoles.size() == 1) {
      RunContentDescriptor descriptor = consoles.iterator().next();
      descriptorConsumer.consume(descriptor);
      descriptorToFront(editor, descriptor);
    }
    else if (consoles.size() > 1) {
      final JList list = new JBList(consoles);
      final Icon icon = DefaultRunExecutor.getRunExecutorInstance().getIcon();
      list.setCellRenderer(new ListCellRendererWrapper<RunContentDescriptor>(list.getCellRenderer()) {
        @Override
        public void customize(final JList list,
                              final RunContentDescriptor value,
                              final int index,
                              final boolean selected,
                              final boolean hasFocus) {
          setText(value.getDisplayName());
          setIcon(icon);
        }
      });

      final PopupChooserBuilder builder = new PopupChooserBuilder(list);
      builder.setTitle(selectDialogTitle);

      builder.setItemChoosenCallback(new Runnable() {
        @Override
        public void run() {
          final Object selectedValue = list.getSelectedValue();
          if (selectedValue instanceof RunContentDescriptor) {
            RunContentDescriptor descriptor = (RunContentDescriptor)selectedValue;
            descriptorConsumer.consume(descriptor);
            descriptorToFront(editor, descriptor);
          }
        }
      }).createPopup().showInBestPositionFor(editor);
    }
  }

  private static void descriptorToFront(Editor editor, RunContentDescriptor descriptor) {
    ExecutionManager.getInstance(editor.getProject()).getContentManager()
      .toFrontRunContent(DefaultRunExecutor.getRunExecutorInstance(), descriptor);
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
    final String title = mode.getTitle() != null ? mode.getTitle() : "Please wait...";
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
      }
      else {
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
          myProgressIndicator.setText("Please wait...");
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
          }
          finally {
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
