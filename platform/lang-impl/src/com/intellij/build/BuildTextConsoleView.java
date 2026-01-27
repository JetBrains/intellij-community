// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.build;

import com.intellij.build.events.BuildEvent;
import com.intellij.build.events.BuildIssueEvent;
import com.intellij.build.events.EventResult;
import com.intellij.build.events.Failure;
import com.intellij.build.events.FailureResult;
import com.intellij.build.events.FileMessageEvent;
import com.intellij.build.events.FinishEvent;
import com.intellij.build.events.MessageEvent;
import com.intellij.build.events.OutputBuildEvent;
import com.intellij.execution.filters.Filter;
import com.intellij.execution.filters.HyperlinkInfo;
import com.intellij.execution.filters.LazyFileHyperlinkInfo;
import com.intellij.execution.impl.ConsoleViewImpl;
import com.intellij.execution.process.AnsiEscapeDecoder;
import com.intellij.execution.process.ProcessOutputType;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.IJSwingUtilities;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import io.opentelemetry.api.internal.StringUtils;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.event.HyperlinkEvent;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Vladislav.Soroka
 */
public final class BuildTextConsoleView extends ConsoleViewImpl implements BuildConsoleView {

  private static final Pattern TAG_PATTERN = Pattern.compile("<[^>]*>");
  private static final Pattern A_PATTERN = Pattern.compile("<a ([^>]* )?href=[\"']([^>]*)[\"'][^>]*>");
  private static final String A_CLOSING = "</a>";
  private static final Set<@NlsSafe String> NEW_LINES = Set.of("<br>", "</br>", "<br/>", "<p>", "</p>", "<p/>", "<pre>", "</pre>");

  private final AnsiEscapeDecoder myAnsiEscapeDecoder = new AnsiEscapeDecoder();

  public BuildTextConsoleView(@NotNull Project project, boolean viewer, @NotNull List<? extends Filter> executionFilters) {
    super(project, GlobalSearchScope.allScope(project), viewer, true);
    executionFilters.forEach(this::addMessageFilter);
  }

  @Override
  public void onEvent(@NotNull Object buildId, @NotNull BuildEvent event) {
    onEvent(event);
  }

  @ApiStatus.Internal
  public void onEvent(@NotNull BuildEvent event) {
    switch (event) {
      case BuildIssueEvent buildIssueEvent -> onBuildIssueEvent(buildIssueEvent);
      case FileMessageEvent fileMessageEvent -> onFileMessageEvent(fileMessageEvent);
      case MessageEvent messageEvent -> onMessageEvent(messageEvent);
      case FinishEvent finishEvent -> onFinishEvent(finishEvent);
      case OutputBuildEvent outputEvent -> onOutputEvent(outputEvent);
      default -> onBuildEvent(event);
    }
  }

  private void onBuildIssueEvent(@NotNull BuildIssueEvent event) {
    var contentType = getContentType(event.getResult().getKind());
    var quickFixes = ContainerUtil.map2Map(event.getIssue().getQuickFixes(), it -> new Pair<>(it.getId(), it));
    printHtml(event.getIssue().getDescription(), contentType, hyperlinkEvent -> {
      var quickFix = quickFixes.get(hyperlinkEvent.getDescription());
      if (quickFix != null) {
        quickFix.runQuickFix(getProject(), BuildConsoleUtils.getDataContext(this));
      }
    });
  }

  private void onFileMessageEvent(@NotNull FileMessageEvent event) {
    var contentType = getContentType(event.getResult().getKind());
    var description = event.getDescription();
    if (description != null) {
      print(description, contentType);
      return;
    }
    printFilePosition(event.getFilePosition());
    print(": ", ConsoleViewContentType.NORMAL_OUTPUT);
    print(event.getMessage(), contentType);
  }

  private void onMessageEvent(@NotNull MessageEvent event) {
    var contentType = getContentType(event.getResult().getKind());
    var details = event.getResult().getDetails();
    if (!StringUtils.isNullOrEmpty(details)) {
      printHtml(details, contentType, null);
    }
  }

  private void onFinishEvent(@NotNull FinishEvent event) {
    var eventResult = event.getResult();
    if (eventResult instanceof FailureResult failureResult) {
      var iterator = failureResult.getFailures().iterator();
      while (iterator.hasNext()) {
        var failure = iterator.next();
        printFailure(failure);
        if (iterator.hasNext()) {
          print("\n\n", ConsoleViewContentType.NORMAL_OUTPUT);
        }
      }
    }
  }

  private void onOutputEvent(@NotNull OutputBuildEvent event) {
    print(event.getMessage(), event.getOutputType());
  }

  private void onBuildEvent(@NotNull BuildEvent event) {
    print(ObjectUtils.notNull(event.getDescription(), event.getMessage()), ProcessOutputType.STDOUT);
  }

  /**
   * @deprecated Use the {@link ConsoleViewImpl#print} function instead
   */
  @Deprecated
  public void append(@NotNull String text, boolean isStdOut) {
    print(text, isStdOut ? ProcessOutputType.STDOUT : ProcessOutputType.STDERR);
  }

  public void print(@NotNull String text, @NotNull Key<?> outputType) {
    myAnsiEscapeDecoder.escapeText(text, outputType, (decodedText, attributes) ->
      print(decodedText, ConsoleViewContentType.getConsoleViewType(attributes))
    );
  }

  private void printFilePosition(@NotNull FilePosition filePosition) {
    var positionFile = filePosition.getFile();
    if (positionFile == null) {
      return;
    }
    var hyperlinkText = new StringJoiner(":");
    hyperlinkText.add(positionFile.getName());
    var positionStartLine = filePosition.getStartLine();
    if (positionStartLine > 0) {
      hyperlinkText.add(Integer.toString(positionStartLine + 1));
    }
    var positionStartColumn = filePosition.getStartColumn();
    if (positionStartColumn > 0) {
      hyperlinkText.add(Integer.toString(positionStartColumn + 1));
    }
    var hyperlinkInfo = new LazyFileHyperlinkInfo(getProject(), positionFile.getPath(), positionStartLine, positionStartColumn);
    print(hyperlinkText.toString(), ConsoleViewContentType.NORMAL_OUTPUT, hyperlinkInfo);
  }

  @Internal
  public void printFailure(@NotNull Failure failure) {
    var errorMessage = ObjectUtils.doIfNotNull(failure.getError(), it -> it.getMessage());
    var text = ObjectUtils.coalesce(failure.getDescription(), failure.getMessage(), errorMessage);
    if (!StringUtils.isNullOrEmpty(text)) {
      var notification = failure.getNotification();
      var notificationListener = notification == null ? null : notification.getListener();
      printHtml(text, ConsoleViewContentType.ERROR_OUTPUT, notification == null || notificationListener == null ? null : it ->
        notificationListener.hyperlinkUpdate(notification, it)
      );
    }
  }

  private void printHtml(
    @NotNull String text,
    @NotNull ConsoleViewContentType contentType,
    @Nullable Consumer<@NotNull HyperlinkEvent> hyperlinkListener
  ) {
    String content = StringUtil.convertLineSeparators(text);
    while (true) {
      Matcher tagMatcher = TAG_PATTERN.matcher(content);
      if (!tagMatcher.find()) {
        print(content, contentType);
        break;
      }
      String tagStart = tagMatcher.group();
      print(content.substring(0, tagMatcher.start()), contentType);
      Matcher aMatcher = A_PATTERN.matcher(tagStart);
      if (aMatcher.matches()) {
        final String href = aMatcher.group(2);
        int linkEnd = content.indexOf(A_CLOSING, tagMatcher.end());
        if (linkEnd > 0) {
          var hyperlinkText = content.substring(tagMatcher.end(), linkEnd)
            .replaceAll(TAG_PATTERN.pattern(), "");
          printHyperlink(hyperlinkText, new HyperlinkInfo() {
            @Override
            public void navigate(@NotNull Project project) {
              if (hyperlinkListener != null) {
                hyperlinkListener.accept(IJSwingUtilities.createHyperlinkEvent(href, getComponent()));
              }
            }
          });
          content = content.substring(linkEnd + A_CLOSING.length());
          continue;
        }
      }
      if (NEW_LINES.contains(tagStart)) {
        print("\n", contentType);
      }
      else {
        print(content.substring(tagMatcher.start(), tagMatcher.end()), contentType);
      }
      content = content.substring(tagMatcher.end());
    }

    print("\n", contentType);
  }

  private static @NotNull ConsoleViewContentType getContentType(@NotNull MessageEvent.Kind kind) {
    return switch (kind) {
      case ERROR, WARNING -> ConsoleViewContentType.ERROR_OUTPUT;
      case INFO, SIMPLE -> ConsoleViewContentType.NORMAL_OUTPUT;
      case STATISTICS -> ConsoleViewContentType.SYSTEM_OUTPUT;
    };
  }
}

