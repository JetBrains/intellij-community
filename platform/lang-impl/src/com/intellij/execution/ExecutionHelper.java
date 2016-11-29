/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.execution.ui.RunContentManager;
import com.intellij.ide.errorTreeView.NewErrorTreeViewPanel;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
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
import com.intellij.pom.NonNavigatable;
import com.intellij.ui.ListCellRendererWrapper;
import com.intellij.ui.components.JBList;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.ui.content.MessageView;
import com.intellij.util.*;
import com.intellij.util.concurrency.Semaphore;
import com.intellij.util.containers.ContainerUtil;
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

  public static void showErrors(
    @NotNull final Project myProject,
    @NotNull final List<? extends Exception> errors,
    @NotNull final String tabDisplayName,
    @Nullable final VirtualFile file) {
    showExceptions(myProject, errors, Collections.<Exception>emptyList(), tabDisplayName, file);
  }

  public static void showExceptions(
    @NotNull final Project myProject,
    @NotNull final List<? extends Exception> errors,
    @NotNull final List<? extends Exception> warnings,
    @NotNull final String tabDisplayName,
    @Nullable final VirtualFile file) {
    if (ApplicationManager.getApplication().isUnitTestMode() && !errors.isEmpty()) {
      throw new RuntimeException(errors.get(0));
    }
    ApplicationManager.getApplication().invokeLater(() -> {
      if (myProject.isDisposed()) return;
      if (errors.isEmpty() && warnings.isEmpty()) {
        removeContents(null, myProject, tabDisplayName);
        return;
      }

      final ErrorViewPanel errorTreeView = new ErrorViewPanel(myProject);
      try {
        openMessagesView(errorTreeView, myProject, tabDisplayName);
      }
      catch (NullPointerException e) {
        final StringBuilder builder = new StringBuilder();
        builder.append("Exceptions occurred:");
        for (final Exception exception : errors) {
          builder.append("\n");
          builder.append(exception.getMessage());
        }
        builder.append("Warnings occurred:");
        for (final Exception exception : warnings) {
          builder.append("\n");
          builder.append(exception.getMessage());
        }
        Messages.showErrorDialog(builder.toString(), "Execution Error");
        return;
      }

      addMessages(MessageCategory.ERROR, errors, errorTreeView, file, "Unknown Error");
      addMessages(MessageCategory.WARNING, warnings, errorTreeView, file, "Unknown Warning");

      ToolWindowManager.getInstance(myProject).getToolWindow(ToolWindowId.MESSAGES_WINDOW).activate(null);
    });
  }

  private static void addMessages(
    final int messageCategory,
    @NotNull final List<? extends Exception> exceptions,
    @NotNull ErrorViewPanel errorTreeView,
    @Nullable final VirtualFile file,
    @NotNull final String defaultMessage) {
    for (final Exception exception : exceptions) {
      String message = exception.getMessage();

      String[] messages = StringUtil.isNotEmpty(message) ? StringUtil.splitByLines(message) : ArrayUtil.EMPTY_STRING_ARRAY;
      if (messages.length == 0) {
        messages = new String[]{defaultMessage};
      }
      errorTreeView.addMessage(messageCategory, messages, file, -1, -1, null);
    }
  }

  public static void showOutput(@NotNull final Project myProject,
                                @NotNull final ProcessOutput output,
                                @NotNull final String tabDisplayName,
                                @Nullable final VirtualFile file,
                                final boolean activateWindow) {
    final String stdout = output.getStdout();
    final String stderr = output.getStderr();
    if (ApplicationManager.getApplication().isUnitTestMode() && !(stdout.isEmpty() || stderr.isEmpty())) {
      throw new RuntimeException("STDOUT:\n" + stdout + "\nSTDERR:\n" + stderr);
    }

    ApplicationManager.getApplication().invokeLater(() -> {
      if (myProject.isDisposed()) return;

      final String stdOutTitle = "[Stdout]:";
      final String stderrTitle = "[Stderr]:";
      final ErrorViewPanel errorTreeView = new ErrorViewPanel(myProject);
      try {
        openMessagesView(errorTreeView, myProject, tabDisplayName);
      }
      catch (NullPointerException e) {
        Messages.showErrorDialog(stdOutTitle + "\n" + (stdout != null ? stdout : "<empty>") + "\n" + stderrTitle + "\n"
                                 + (stderr != null ? stderr : "<empty>"), "Process Output");
        return;
      }

      if (!StringUtil.isEmpty(stdout)) {
        final String[] stdoutLines = StringUtil.splitByLines(stdout);
        if (stdoutLines.length > 0) {
          if (StringUtil.isEmpty(stderr)) {
            // Only stdout available
            errorTreeView.addMessage(MessageCategory.SIMPLE, stdoutLines, file, -1, -1, null);
          }
          else {
            // both stdout and stderr available, show as groups
            if (file == null) {
              errorTreeView.addMessage(MessageCategory.SIMPLE, stdoutLines, stdOutTitle, NonNavigatable.INSTANCE, null, null, null);
            }
            else {
              errorTreeView.addMessage(MessageCategory.SIMPLE, new String[]{stdOutTitle}, file, -1, -1, null);
              errorTreeView.addMessage(MessageCategory.SIMPLE, new String[]{""}, file, -1, -1, null);
              errorTreeView.addMessage(MessageCategory.SIMPLE, stdoutLines, file, -1, -1, null);
            }
          }
        }
      }
      if (!StringUtil.isEmpty(stderr)) {
        final String[] stderrLines = StringUtil.splitByLines(stderr);
        if (stderrLines.length > 0) {
          if (file == null) {
            errorTreeView.addMessage(MessageCategory.SIMPLE, stderrLines, stderrTitle, NonNavigatable.INSTANCE, null, null, null);
          }
          else {
            errorTreeView.addMessage(MessageCategory.SIMPLE, new String[]{stderrTitle}, file, -1, -1, null);
            errorTreeView.addMessage(MessageCategory.SIMPLE, ArrayUtil.EMPTY_STRING_ARRAY, file, -1, -1, null);
            errorTreeView.addMessage(MessageCategory.SIMPLE, stderrLines, file, -1, -1, null);
          }
        }
      }
      errorTreeView
        .addMessage(MessageCategory.SIMPLE, new String[]{"Process finished with exit code " + output.getExitCode()}, null, -1, -1, null);

      if (activateWindow) {
        ToolWindowManager.getInstance(myProject).getToolWindow(ToolWindowId.MESSAGES_WINDOW).activate(null);
      }
    });
  }

  private static void openMessagesView(@NotNull final ErrorViewPanel errorTreeView,
                                       @NotNull final Project myProject,
                                       @NotNull final String tabDisplayName) {
    CommandProcessor commandProcessor = CommandProcessor.getInstance();
    commandProcessor.executeCommand(myProject, () -> {
      final MessageView messageView = ServiceManager.getService(myProject, MessageView.class);
      final Content content = ContentFactory.SERVICE.getInstance().createContent(errorTreeView, tabDisplayName, true);
      messageView.getContentManager().addContent(content);
      Disposer.register(content, errorTreeView);
      messageView.getContentManager().setSelectedContent(content);
      removeContents(content, myProject, tabDisplayName);
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

  public static Collection<RunContentDescriptor> findRunningConsoleByTitle(final Project project,
                                                                           @NotNull final NotNullFunction<String, Boolean> titleMatcher) {
    return findRunningConsole(project, selectedContent -> titleMatcher.fun(selectedContent.getDisplayName()));
  }

  public static Collection<RunContentDescriptor> findRunningConsole(@NotNull Project project,
                                                                    @NotNull NotNullFunction<RunContentDescriptor, Boolean> descriptorMatcher) {
    RunContentManager contentManager = ExecutionManager.getInstance(project).getContentManager();
    final RunContentDescriptor selectedContent = contentManager.getSelectedContent();
    if (selectedContent != null) {
      final ToolWindow toolWindow = contentManager.getToolWindowByDescriptor(selectedContent);
      if (toolWindow != null && toolWindow.isVisible()) {
        if (descriptorMatcher.fun(selectedContent)) {
          return Collections.singletonList(selectedContent);
        }
      }
    }

    final ArrayList<RunContentDescriptor> result = ContainerUtil.newArrayList();
    for (RunContentDescriptor runContentDescriptor : contentManager.getAllDescriptors()) {
      if (descriptorMatcher.fun(runContentDescriptor)) {
        result.add(runContentDescriptor);
      }
    }
    return result;
  }

  public static List<RunContentDescriptor> collectConsolesByDisplayName(@NotNull Project project,
                                                                        @NotNull NotNullFunction<String, Boolean> titleMatcher) {
    List<RunContentDescriptor> result = new SmartList<>();
    for (RunContentDescriptor runContentDescriptor : ExecutionManager.getInstance(project).getContentManager().getAllDescriptors()) {
      if (titleMatcher.fun(runContentDescriptor.getDisplayName())) {
        result.add(runContentDescriptor);
      }
    }
    return result;
  }

  public static void selectContentDescriptor(final @NotNull DataContext dataContext,
                                             final @NotNull Project project,
                                             @NotNull Collection<RunContentDescriptor> consoles,
                                             String selectDialogTitle, final Consumer<RunContentDescriptor> descriptorConsumer) {
    if (consoles.size() == 1) {
      RunContentDescriptor descriptor = consoles.iterator().next();
      descriptorConsumer.consume(descriptor);
      descriptorToFront(project, descriptor);
    }
    else if (consoles.size() > 1) {
      final JList list = new JBList(consoles);
      final Icon icon = DefaultRunExecutor.getRunExecutorInstance().getIcon();
      list.setCellRenderer(new ListCellRendererWrapper<RunContentDescriptor>() {
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

      builder.setItemChoosenCallback(() -> {
        final Object selectedValue = list.getSelectedValue();
        if (selectedValue instanceof RunContentDescriptor) {
          RunContentDescriptor descriptor = (RunContentDescriptor)selectedValue;
          descriptorConsumer.consume(descriptor);
          descriptorToFront(project, descriptor);
        }
      }).createPopup().showInBestPositionFor(dataContext);
    }
  }

  private static void descriptorToFront(@NotNull final Project project, @NotNull final RunContentDescriptor descriptor) {
    ApplicationManager.getApplication().invokeLater(() -> {
      ToolWindow toolWindow = ExecutionManager.getInstance(project).getContentManager().getToolWindowByDescriptor(descriptor);
      if (toolWindow != null) {
        toolWindow.show(null);
        //noinspection ConstantConditions
        toolWindow.getContentManager().setSelectedContent(descriptor.getAttachedContent());
      }
    }, project.getDisposed());
  }

  public static class ErrorViewPanel extends NewErrorTreeViewPanel {
    public ErrorViewPanel(final Project project) {
      super(project, "reference.toolWindows.messages");
    }

    @Override
    protected boolean canHideWarnings() {
      return false;
    }
  }


  public static void executeExternalProcess(@Nullable final Project myProject,
                                            @NotNull final ProcessHandler processHandler,
                                            @NotNull final ExecutionMode mode,
                                            @NotNull final GeneralCommandLine cmdline) {
    executeExternalProcess(myProject, processHandler, mode, cmdline.getCommandLineString());
  }

  public static void executeExternalProcess(@Nullable final Project myProject,
                                            @NotNull final ProcessHandler processHandler,
                                            @NotNull final ExecutionMode mode,
                                            @NotNull final String presentableCmdline) {
    final String title = mode.getTitle() != null ? mode.getTitle() : "Please wait...";
    final Runnable process;
    if (mode.cancelable()) {
      process = createCancelableExecutionProcess(processHandler, mode.shouldCancelFun());
    }
    else {
      if (mode.getTimeout() <= 0) {
        process = () -> processHandler.waitFor();
      }
      else {
        process = createTimeLimitedExecutionProcess(processHandler, mode, presentableCmdline);
      }
    }
    if (mode.withModalProgress()) {
      ProgressManager.getInstance().runProcessWithProgressSynchronously(process, title, mode.cancelable(), myProject,
                                                                        mode.getProgressParentComponent());
    }
    else if (mode.inBackGround()) {
      final Task task = new Task.Backgroundable(myProject, title, mode.cancelable()) {
        @Override
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

      private final Runnable myWaitThread = () -> {
        try {
          processHandler.waitFor();
        }
        finally {
          mySemaphore.up();
        }
      };

      private final Runnable myCancelListener = new Runnable() {
        @Override
        public void run() {
          while (true) {
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

      @Override
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

  private static Runnable createTimeLimitedExecutionProcess(final ProcessHandler processHandler,
                                                            final ExecutionMode mode,
                                                            @NotNull final String presentableCmdline) {
    return new Runnable() {
      private final Semaphore mySemaphore = new Semaphore();

      private final Runnable myProcessThread = () -> {
        try {
          final boolean finished = processHandler.waitFor(1000 * mode.getTimeout());
          if (!finished) {
            mode.getTimeoutCallback().consume(mode, presentableCmdline);
            processHandler.destroyProcess();
          }
        }
        finally {
          mySemaphore.up();
        }
      };

      @Override
      public void run() {
        mySemaphore.down();
        ApplicationManager.getApplication().executeOnPooledThread(myProcessThread);

        mySemaphore.waitFor();
      }
    };
  }
}
