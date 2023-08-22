// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compiler.progress;

import com.intellij.build.*;
import com.intellij.build.events.MessageEvent;
import com.intellij.build.issue.BuildIssue;
import com.intellij.build.progress.BuildProgress;
import com.intellij.build.progress.BuildProgressDescriptor;
import com.intellij.compiler.impl.CompilerPropertiesAction;
import com.intellij.compiler.impl.ExcludeFromCompileAction;
import com.intellij.compiler.impl.ExitStatus;
import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.filters.RegexpFilter;
import com.intellij.execution.filters.UrlFilter;
import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.compiler.CompilerMessage;
import com.intellij.openapi.compiler.CompilerMessageCategory;
import com.intellij.openapi.compiler.JavaCompilerBundle;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ex.ProgressIndicatorEx;
import com.intellij.pom.Navigatable;
import com.intellij.util.SmartList;
import com.intellij.util.containers.Stack;
import org.jetbrains.annotations.*;

import java.io.File;
import java.util.*;

import static com.intellij.execution.filters.RegexpFilter.*;
import static com.intellij.openapi.util.text.StringUtil.*;
import static com.intellij.openapi.vfs.VfsUtilCore.virtualToIoFile;

@ApiStatus.Internal
public class BuildOutputService implements BuildViewService {
  private static final ExtensionPointName<BuildIssueContributor> BUILD_ISSUE_EP =
    ExtensionPointName.create("com.intellij.compiler.buildIssueContributor");

  private static final @NonNls String ANSI_RESET = "\u001B[0m";
  private static final @NonNls String ANSI_RED = "\u001B[31m";
  private static final @NonNls String ANSI_YELLOW = "\u001B[33m";
  private static final @NonNls String ANSI_BOLD = "\u001b[1m";
  private final @NotNull Project myProject;
  private final @NotNull BuildProgress<BuildProgressDescriptor> myBuildProgress;
  private final @NotNull @NlsContexts.TabTitle String myContentName;
  private final ConsolePrinter myConsolePrinter;

  public BuildOutputService(@NotNull Project project, @NotNull @NlsContexts.TabTitle String contentName) {
    myProject = project;
    myContentName = contentName;
    myBuildProgress = BuildViewManager.createBuildProgress(project);
    myConsolePrinter = new ConsolePrinter(myBuildProgress);
  }

  @Override
  public void registerCloseAction(Runnable onClose) { }

  @Override
  public void onProgressChange(Object sessionId, ProgressIndicator indicator) { }

  @Override
  public void onStart(Object sessionId, long startCompilationStamp, Runnable restartWork, ProgressIndicator indicator) {
    List<AnAction> restartActions = getRestartActions(restartWork, indicator);
    List<AnAction> contextActions = getContextActions();
    String title;
    if (myContentName.equals(JavaCompilerBundle.message("compiler.content.name.rebuild")) ||
        myContentName.equals(JavaCompilerBundle.message("compiler.content.name.recompile")) ||
        myContentName.equals(JavaCompilerBundle.message("compiler.content.name.make"))) {
      title = myProject.getName();
    }
    else {
      title = capitalize(wordsToBeginFromLowerCase(myContentName));
    }

    DefaultBuildDescriptor buildDescriptor =
      new DefaultBuildDescriptor(sessionId, title, notNullize(myProject.getBasePath()), startCompilationStamp)
        .withRestartActions(restartActions.toArray(AnAction.EMPTY_ARRAY))
        .withAction(new CompilerPropertiesAction())
        .withExecutionFilter(new ModuleLinkFilter(myProject))
        .withExecutionFilter(new RegexpFilter(myProject, FILE_PATH_MACROS + ":" + LINE_MACROS + ":" + COLUMN_MACROS))
        .withExecutionFilter(new UrlFilter(myProject))
        .withContextAction(node -> {
          return new ExcludeFromCompileAction(myProject) {
            @Override
            protected @Nullable VirtualFile getFile() {
              List<Navigatable> navigatables = node.getNavigatables();
              if (navigatables.size() != 1) return null;
              Navigatable navigatable = navigatables.get(0);
              if (navigatable instanceof OpenFileDescriptor) {
                return ((OpenFileDescriptor)navigatable).getFile();
              }
              else if (navigatable instanceof FileNavigatable) {
                OpenFileDescriptor fileDescriptor = ((FileNavigatable)navigatable).getFileDescriptor();
                return fileDescriptor != null ? fileDescriptor.getFile() : null;
              }
              return null;
            }
          };
        })
        .withContextActions(contextActions.toArray(AnAction.EMPTY_ARRAY));

    myBuildProgress.start(new BuildProgressDescriptor() {
      @NotNull
      @Override
      public String getTitle() {
        return buildDescriptor.getTitle();
      }

      @Override
      public @NotNull BuildDescriptor getBuildDescriptor() {
        return buildDescriptor;
      }
    });
    addIndicatorDelegate(indicator);
  }

  @Override
  public void onEnd(Object sessionId, ExitStatus exitStatus, long endBuildStamp) {
    String message;
    if (exitStatus == ExitStatus.ERRORS) {
      message = BuildBundle.message("build.messages.failed", wordsToBeginFromLowerCase(myContentName));
      myBuildProgress.fail(endBuildStamp, message);
    }
    else if (exitStatus == ExitStatus.CANCELLED) {
      message = BuildBundle.message("build.messages.cancelled", wordsToBeginFromLowerCase(myContentName));
      myBuildProgress.cancel(endBuildStamp, message);
    }
    else {
      boolean isUpToDate = exitStatus == ExitStatus.UP_TO_DATE;
      if (JavaCompilerBundle.message("classes.up.to.date.check").equals(myContentName)) {
        if (isUpToDate) {
          myConsolePrinter.print(JavaCompilerBundle.message("compiler.build.messages.classes.check.uptodate"), MessageEvent.Kind.SIMPLE);
        }
        else {
          myConsolePrinter.print(JavaCompilerBundle.message("compiler.build.messages.classes.check.outdated"), MessageEvent.Kind.SIMPLE);
        }
      }
      message = BuildBundle.message("build.messages.finished", wordsToBeginFromLowerCase(myContentName));
      myBuildProgress.finish(endBuildStamp, isUpToDate, message);
    }
  }

  @Override
  public void addMessage(Object sessionId, CompilerMessage compilerMessage) {
    MessageEvent.Kind kind = convertCategory(compilerMessage.getCategory());
    VirtualFile virtualFile = compilerMessage.getVirtualFile();
    Navigatable navigatable = compilerMessage.getNavigatable();
    String title = getMessageTitle(compilerMessage);
    BuildIssue issue = buildIssue(compilerMessage.getModuleNames(), title, compilerMessage.getMessage(), kind, virtualFile, navigatable);
    if (issue != null) {
      myBuildProgress.buildIssue(issue, kind);
    }
    else if (virtualFile != null) {
      File file = virtualToIoFile(virtualFile);
      FilePosition filePosition;
      if (navigatable instanceof OpenFileDescriptor fileDescriptor) {
        int column = fileDescriptor.getColumn();
        int line = fileDescriptor.getLine();
        filePosition = new FilePosition(file, line, column);
      }
      else {
        filePosition = new FilePosition(file, 0, 0);
      }

      myBuildProgress.fileMessage(title, compilerMessage.getMessage(), kind, filePosition);
    }
    else {
      if (kind == MessageEvent.Kind.ERROR || kind == MessageEvent.Kind.WARNING) {
        myBuildProgress.message(title, compilerMessage.getMessage(), kind, navigatable);
      }
      myConsolePrinter.print(compilerMessage.getMessage(), kind);
    }
  }

  @Nullable
  private BuildIssue buildIssue(@NotNull Collection<String> moduleNames,
                                @NotNull String title,
                                @NotNull String message,
                                @NotNull MessageEvent.Kind kind,
                                @Nullable VirtualFile virtualFile,
                                @Nullable Navigatable navigatable) {
    return BUILD_ISSUE_EP.computeSafeIfAny(contributor -> {
      return contributor.createBuildIssue(myProject, moduleNames, title, message, kind, virtualFile, navigatable);
    });
  }

  @NotNull
  private List<AnAction> getRestartActions(Runnable restartWork, ProgressIndicator indicator) {
    List<AnAction> restartActions = new SmartList<>();
    if (restartWork != null) {
      AnAction restartAction = new DumbAwareAction(
        ExecutionBundle.messagePointer("rerun.configuration.action.name", escapeMnemonics(myContentName)),
        Presentation.NULL_STRING, AllIcons.Actions.Compile) {
        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
          restartWork.run();
        }

        @Override
        public void update(@NotNull AnActionEvent e) {
          e.getPresentation().setEnabled(!indicator.isRunning());
        }

        @Override
        public @NotNull ActionUpdateThread getActionUpdateThread() {
          return ActionUpdateThread.BGT;
        }
      };
      restartActions.add(restartAction);
    }
    AnAction stopAction = new DumbAwareAction(IdeBundle.messagePointer("action.stop"), AllIcons.Actions.Suspend) {
      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        indicator.cancel();
      }

      @Override
      public void update(@NotNull AnActionEvent event) {
        event.getPresentation().setEnabled(indicator.isRunning());
      }

      @Override
      public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
      }
    };
    restartActions.add(stopAction);
    return restartActions;
  }

  private void addIndicatorDelegate(ProgressIndicator indicator) {
    if (!(indicator instanceof ProgressIndicatorEx)) {
      return;
    }
    ((ProgressIndicatorEx)indicator).addStateDelegate(new CompilerMessagesService.DummyProgressIndicator() {
      private final Map<String, Set<String>> mySeenMessages = new HashMap<>();
      @NlsSafe
      private String lastMessage = null;
      private Stack<@NlsContexts.ProgressText String> myTextStack;

      @Override
      public void setText(@Nls(capitalization = Nls.Capitalization.Sentence) String text) {
        addIndicatorNewMessagesAsBuildOutput(text);
      }

      @Override
      public void pushState() {
        getTextStack().push(indicator.getText());
      }

      @Override
      public void setFraction(double fraction) {
        myBuildProgress.progress(lastMessage, 100, (long)(fraction * 100), "%");
      }

      @NotNull
      private Stack<@NlsContexts.ProgressText String> getTextStack() {
        Stack<@NlsContexts.ProgressText String> stack = myTextStack;
        if (stack == null) myTextStack = stack = new Stack<>(2);
        return stack;
      }

      private void addIndicatorNewMessagesAsBuildOutput(@Nls String msg) {
        Stack<@NlsContexts.ProgressText String> textStack = getTextStack();
        if (!textStack.isEmpty() && msg.equals(textStack.peek())) {
          textStack.pop();
          return;
        }
        if (isEmptyOrSpaces(msg) || msg.equals(lastMessage)) return;
        lastMessage = msg;

        int start = msg.indexOf("[");
        if (start >= 1) {
          int end = msg.indexOf(']', start + 1);
          if (end != -1) {
            String buildTargetNameCandidate = msg.substring(start + 1, end);
            Set<String> targets = mySeenMessages.computeIfAbsent(buildTargetNameCandidate, unused -> new HashSet<>());
            boolean isSeenMessage = !targets.add(msg.substring(0, start));
            if (isSeenMessage) return;
          }
        }
        myConsolePrinter.print(msg, MessageEvent.Kind.SIMPLE);
      }
    });
  }

  @NotNull
  private static List<AnAction> getContextActions() {
    List<AnAction> contextActions = new SmartList<>();
    ActionGroup compilerErrorsViewPopupGroup =
      (ActionGroup)ActionManager.getInstance().getAction(IdeActions.GROUP_COMPILER_ERROR_VIEW_POPUP);
    if (compilerErrorsViewPopupGroup != null) {
      Collections.addAll(contextActions, compilerErrorsViewPopupGroup.getChildren(null));
    }
    return contextActions;
  }

  @Nls
  private static String getMessageTitle(@NotNull CompilerMessage compilerMessage) {
    String message = null;
    String[] messages = splitByLines(compilerMessage.getMessage());
    if (messages.length > 1) {
      final String line0 = messages[0];
      final String line1 = messages[1];
      final int colonIndex = line1.indexOf(':');
      if (colonIndex > 0) {
        String part1 = line1.substring(0, colonIndex).trim();
        // extract symbol information from the compiler message of the following template:
        // java: cannot find symbol
        //  symbol:   class AClass
        //  location: class AnotherClass
        if ("symbol".equals(part1)) {
          String symbol = line1.substring(colonIndex + 1).trim();
          message = line0 + " " + symbol;
        }
      }
    }
    if (message == null) {
      message = messages[0];
    }
    return trimEnd(trimStart(message, "java: "), '.'); //NON-NLS
  }

  @NotNull
  private static MessageEvent.Kind convertCategory(@NotNull CompilerMessageCategory category) {
    return switch (category) {
      case ERROR -> MessageEvent.Kind.ERROR;
      case WARNING -> MessageEvent.Kind.WARNING;
      case INFORMATION -> MessageEvent.Kind.INFO;
      case STATISTICS -> MessageEvent.Kind.STATISTICS;
    };
  }

  private static class ConsolePrinter {
    private final @NotNull BuildProgress<BuildProgressDescriptor> progress;
    private volatile boolean isNewLinePosition = true;

    private ConsolePrinter(@NotNull BuildProgress<BuildProgressDescriptor> progress) {this.progress = progress;}

    private void print(@NotNull @Nls String message, @NotNull MessageEvent.Kind kind) {
      String text = wrapWithAnsiColor(kind, message);
      if (!isNewLinePosition && !startsWithChar(message, '\r')) {
        text = '\n' + text;
      }
      isNewLinePosition = endsWithLineBreak(message);
      progress.output(text, kind != MessageEvent.Kind.ERROR);
    }

    @Nls
    private static String wrapWithAnsiColor(MessageEvent.Kind kind, @Nls String message) {
      if (kind == MessageEvent.Kind.SIMPLE) return message;
      @NlsSafe
      String color;
      if (kind == MessageEvent.Kind.ERROR) {
        color = ANSI_RED;
      }
      else if (kind == MessageEvent.Kind.WARNING) {
        color = ANSI_YELLOW;
      }
      else {
        color = ANSI_BOLD;
      }
      @NlsSafe final String ansiReset = ANSI_RESET;
      return color + message + ansiReset;
    }
  }
}
