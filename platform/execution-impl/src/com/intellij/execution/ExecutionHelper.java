// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.execution;

import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.impl.ExecutionManagerImpl;
import com.intellij.execution.process.*;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.execution.ui.RunContentManager;
import com.intellij.ide.errorTreeView.NewErrorTreeViewPanel;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.pom.NonNavigatable;
import com.intellij.ui.SimpleListCellRenderer;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.ui.content.ContentManagerUtil;
import com.intellij.ui.content.MessageView;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.Consumer;
import com.intellij.util.NotNullFunction;
import com.intellij.util.SmartList;
import com.intellij.util.concurrency.Semaphore;
import com.intellij.util.ui.MessageCategory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * @author Roman Chernyatchik
 */
public final class ExecutionHelper {
  private static final Logger LOG = Logger.getInstance(ExecutionHelper.class);

  private ExecutionHelper() {
  }

  public static void showErrors(
    final @NotNull Project myProject,
    final @NotNull List<? extends Exception> errors,
    final @NotNull @NlsContexts.TabTitle String tabDisplayName,
    final @Nullable VirtualFile file) {
    showExceptions(myProject, errors, Collections.emptyList(), tabDisplayName, file);
  }

  public static void showExceptions(
    final @NotNull Project myProject,
    final @NotNull List<? extends Exception> errors,
    final @NotNull List<? extends Exception> warnings,
    final @NotNull @NlsContexts.TabTitle String tabDisplayName,
    final @Nullable VirtualFile file) {

    errors.forEach(it -> {
      LOG.warn(tabDisplayName + " error: " + it.getMessage());
    });
    warnings.forEach(it -> {
      LOG.warn(tabDisplayName + " warning: " + it.getMessage());
    });

    ApplicationManager.getApplication().invokeLater(() -> {
      if (myProject.isDisposed()) return;
      if (errors.isEmpty() && warnings.isEmpty()) {
        ContentManagerUtil.cleanupContents(null, myProject, tabDisplayName);
        return;
      }

      final ErrorViewPanel errorTreeView = new ErrorViewPanel(myProject);
      try {
        openMessagesView(errorTreeView, myProject, tabDisplayName);
      }
      catch (NullPointerException e) {
        String errorText = "";
        if (!errors.isEmpty()) {
          StringBuilder builder = new StringBuilder();
          for (final Exception exception : errors) {
            builder.append("\n");
            builder.append(exception.getMessage());
          }
          errorText = ExecutionBundle.message("exception.error.text") + builder;
        }
        String warningText = "";
        if (!warnings.isEmpty()) {
          StringBuilder builder = new StringBuilder();
          for (final Exception exception : warnings) {
            builder.append("\n");
            builder.append(exception.getMessage());
          }
          warningText = ExecutionBundle.message("warning.error.text") + builder;
        }
        Messages.showErrorDialog(errorText + (errorText.isEmpty() || warningText.isEmpty() ? "" : "\n") + warningText,
                                 ExecutionBundle.message("execution.error"));
        return;
      }

      addMessages(MessageCategory.ERROR, errors, errorTreeView, file, "Unknown Error");
      addMessages(MessageCategory.WARNING, warnings, errorTreeView, file, "Unknown Warning");

      final ToolWindow window = ToolWindowManager.getInstance(myProject).getToolWindow(ToolWindowId.MESSAGES_WINDOW);
      if (window != null) {
       window.activate(null);
      }
    });
  }

  private static void addMessages(
    final int messageCategory,
    final @NotNull List<? extends Exception> exceptions,
    @NotNull ErrorViewPanel errorTreeView,
    final @Nullable VirtualFile file,
    final @NotNull String defaultMessage) {
    for (final Exception exception : exceptions) {
      String message = exception.getMessage();

      String[] messages = StringUtil.isNotEmpty(message) ? StringUtil.splitByLines(message) : ArrayUtilRt.EMPTY_STRING_ARRAY;
      if (messages.length == 0) {
        messages = new String[]{defaultMessage};
      }
      errorTreeView.addMessage(messageCategory, messages, file, -1, -1, null);
    }
  }

  public static void showOutput(final @NotNull Project myProject,
                                final @NotNull ProcessOutput output,
                                final @NotNull @NlsContexts.TabTitle String tabDisplayName,
                                final @Nullable VirtualFile file,
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
        Messages.showErrorDialog(stdOutTitle + "\n" + stdout + "\n" + stderrTitle + "\n" + stderr,
                                 ExecutionBundle.message("process.output"));
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
            errorTreeView.addMessage(MessageCategory.SIMPLE, ArrayUtilRt.EMPTY_STRING_ARRAY, file, -1, -1, null);
            errorTreeView.addMessage(MessageCategory.SIMPLE, stderrLines, file, -1, -1, null);
          }
        }
      }
      errorTreeView
        .addMessage(MessageCategory.SIMPLE, new String[]{"Process finished with exit code " + output.getExitCode()}, null, -1, -1, null);

      if (activateWindow) {
        final ToolWindow toolWindow = ToolWindowManager.getInstance(myProject).getToolWindow(ToolWindowId.MESSAGES_WINDOW);
        if (toolWindow != null) {
          toolWindow.activate(null);
        }
      }
    });
  }

  private static void openMessagesView(final @NotNull ErrorViewPanel errorTreeView,
                                       final @NotNull Project myProject,
                                       final @NotNull @NlsContexts.TabTitle String tabDisplayName) {
    CommandProcessor commandProcessor = CommandProcessor.getInstance();
    commandProcessor.executeCommand(myProject, () -> {
      final MessageView messageView = MessageView.getInstance(myProject);
      messageView.runWhenInitialized(() -> {
        final Content content = ContentFactory.getInstance().createContent(errorTreeView, tabDisplayName, true);
        messageView.getContentManager().addContent(content);
        Disposer.register(content, errorTreeView);
        messageView.getContentManager().setSelectedContent(content);
        ContentManagerUtil.cleanupContents(content, myProject, tabDisplayName);
      });
    }, ExecutionBundle.message("open.message.view"), null);
  }

  public static Collection<RunContentDescriptor> findRunningConsoleByTitle(final Project project,
                                                                           final @NotNull NotNullFunction<? super String, Boolean> titleMatcher) {
    return findRunningConsole(project, selectedContent -> titleMatcher.fun(selectedContent.getDisplayName()));
  }

  public static Collection<RunContentDescriptor> findRunningConsole(@NotNull Project project,
                                                                    @NotNull NotNullFunction<? super RunContentDescriptor, Boolean> descriptorMatcher) {
    return ReadAction.compute(() -> {
      RunContentManager contentManager = RunContentManager.getInstance(project);
      final RunContentDescriptor selectedContent = contentManager.getSelectedContent();
      if (selectedContent != null) {
        final ToolWindow toolWindow = contentManager.getToolWindowByDescriptor(selectedContent);
        if (toolWindow != null && toolWindow.isVisible()) {
          if (descriptorMatcher.fun(selectedContent)) {
            return Collections.singletonList(selectedContent);
          }
        }
      }

      final List<RunContentDescriptor> result = new SmartList<>();
      for (RunContentDescriptor runContentDescriptor : contentManager.getAllDescriptors()) {
        if (descriptorMatcher.fun(runContentDescriptor)) {
          result.add(runContentDescriptor);
        }
      }
      return result;
    });
  }

  public static List<RunContentDescriptor> collectConsolesByDisplayName(@NotNull Project project,
                                                                        @NotNull NotNullFunction<? super String, Boolean> titleMatcher) {
    List<RunContentDescriptor> result = new SmartList<>();
    for (RunContentDescriptor runContentDescriptor : ExecutionManagerImpl.getAllDescriptors(project)) {
      if (titleMatcher.fun(runContentDescriptor.getDisplayName())) {
        result.add(runContentDescriptor);
      }
    }
    return result;
  }

  public static void selectContentDescriptor(final @NotNull DataContext dataContext,
                                             final @NotNull Project project,
                                             @NotNull Collection<? extends RunContentDescriptor> contentDescriptors,
                                             @NlsContexts.PopupTitle String selectDialogTitle,
                                             final Consumer<? super RunContentDescriptor> descriptorConsumer) {
    if (contentDescriptors.size() == 1) {
      RunContentDescriptor descriptor = contentDescriptors.iterator().next();
      descriptorConsumer.consume(descriptor);
      descriptorToFront(project, descriptor);
    }
    else if (ApplicationManager.getApplication().isUnitTestMode()) {
      LOG.error("Expected a single content descriptor, got " + contentDescriptors);
    }
    else if (contentDescriptors.size() > 1) {
      final Icon icon = DefaultRunExecutor.getRunExecutorInstance().getIcon();
      JBPopupFactory.getInstance()
        .createPopupChooserBuilder(new ArrayList<>(contentDescriptors))
        .setRenderer(SimpleListCellRenderer.<RunContentDescriptor>create((label, value, index) -> {
          label.setText(value.getDisplayName());
          label.setIcon(icon);
        }))
        .setTitle(selectDialogTitle)
        .setItemChosenCallback(descriptor -> {
          descriptorConsumer.consume(descriptor);
          descriptorToFront(project, descriptor);
        })
        .createPopup()
        .showInBestPositionFor(dataContext);
    }
  }

  private static void descriptorToFront(final @NotNull Project project, final @NotNull RunContentDescriptor descriptor) {
    ApplicationManager.getApplication().invokeLater(() -> {
      RunContentManager manager = RunContentManager.getInstance(project);
      ToolWindow toolWindow = manager.getToolWindowByDescriptor(descriptor);
      if (toolWindow != null) {
        toolWindow.show(null);
        manager.selectRunContent(descriptor);
      }
    }, project.getDisposed());
  }

  static final class ErrorViewPanel extends NewErrorTreeViewPanel {
    ErrorViewPanel(@NotNull Project project) {
      super(project, "reference.toolWindows.messages");
      Disposer.register(project, this);
    }

    @Override
    protected boolean canHideWarnings() {
      return false;
    }
  }


  public static void executeExternalProcess(final @Nullable Project myProject,
                                            final @NotNull ProcessHandler processHandler,
                                            final @NotNull ExecutionMode mode,
                                            final @NotNull GeneralCommandLine cmdline) {
    executeExternalProcess(myProject, processHandler, mode, cmdline.getCommandLineString());
  }

  private static void executeExternalProcess(final @Nullable Project myProject,
                                             final @NotNull ProcessHandler processHandler,
                                             final @NotNull ExecutionMode mode,
                                             final @NotNull String presentableCmdline) {
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      LOG.debug("Running " + presentableCmdline);
      processHandler.waitFor();
      return;
    }

    final String title = mode.getTitle() != null ? mode.getTitle() : ExecutionBundle.message("progress.title.please.wait");
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
    if (mode.withModalProgress() || !mode.inBackGround() && ApplicationManager.getApplication().isDispatchThread()) {
      ProgressManager.getInstance().runProcessWithProgressSynchronously(process, title, mode.cancelable(), myProject,
                                                                        mode.getProgressParentComponent());
    }
    else if (mode.inBackGround()) {
      final Task task = new Task.Backgroundable(myProject, title, mode.cancelable()) {
        @Override
        public void run(final @NotNull ProgressIndicator indicator) {
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

  /**
   * @param cancelableFun supplier indicating that process should be terminated
   */
  private static Runnable createCancelableExecutionProcess(@NotNull ProcessHandler processHandler,
                                                           @Nullable BooleanSupplier cancelableFun) {
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
            if (myProgressIndicator != null && (myProgressIndicator.isCanceled() || !myProgressIndicator.isRunning())
                || cancelableFun != null && cancelableFun.getAsBoolean()
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
          myProgressIndicator.setText(ExecutionBundle.message("please.wait"));
        }

        LOG.assertTrue(myProgressIndicator != null || cancelableFun != null,
                       "Cancelable process must have an opportunity to be canceled!");
        mySemaphore.down();
        ApplicationManager.getApplication().executeOnPooledThread(myWaitThread);
        ApplicationManager.getApplication().executeOnPooledThread(myCancelListener);
        OSProcessHandler.checkEdtAndReadAction(processHandler);
        mySemaphore.waitFor();
      }
    };
  }

  private static Runnable createTimeLimitedExecutionProcess(@NotNull ProcessHandler processHandler,
                                                            @NotNull ExecutionMode mode,
                                                            final @NotNull String presentableCmdline) {
    ProcessOutput outputCollected = new ProcessOutput();
    processHandler.addProcessListener(new ProcessListener() {
      @Override
      public void onTextAvailable(@NotNull ProcessEvent event, @NotNull Key outputType) {
        String eventText = event.getText();
        if (StringUtil.isNotEmpty(eventText)) {
          if (ProcessOutputType.isStdout(outputType)) {
            outputCollected.appendStdout(eventText);
          }
          else if (ProcessOutputType.isStderr(outputType)) {
            outputCollected.appendStderr(eventText);
          }
        }
      }
    });
    Throwable invocatorStack = new Throwable();
    return new Runnable() {
      private final Semaphore mySemaphore = new Semaphore();

      private final Runnable myProcessRunnable = () -> {
        try {
          final boolean finished = processHandler.waitFor(1000L * mode.getTimeout());
          if (!finished) {
            mode.onTimeout(processHandler, presentableCmdline, outputCollected, invocatorStack);
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
        ApplicationManager.getApplication().executeOnPooledThread(myProcessRunnable);
        OSProcessHandler.checkEdtAndReadAction(processHandler);
        mySemaphore.waitFor();
      }
    };
  }
}
