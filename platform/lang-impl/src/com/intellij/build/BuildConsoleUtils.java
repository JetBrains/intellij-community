// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.build;

import com.intellij.build.events.Failure;
import com.intellij.build.issue.BuildIssue;
import com.intellij.build.issue.BuildIssueQuickFix;
import com.intellij.execution.filters.HyperlinkInfo;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.ide.DataManager;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.AtomicNotNullLazyValue;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.IJSwingUtilities;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.event.HyperlinkEvent;
import java.awt.*;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.intellij.openapi.util.text.StringUtil.stripHtml;

/**
 * @author Vladislav.Soroka
 */
public final class BuildConsoleUtils {
  private static final Logger LOG = Logger.getInstance(BuildConsoleUtils.class);
  private static final Pattern TAG_PATTERN = Pattern.compile("<[^>]*>");
  private static final Pattern A_PATTERN = Pattern.compile("<a ([^>]* )?href=[\"']([^>]*)[\"'][^>]*>");
  private static final String A_CLOSING = "</a>";
  private static final Set<@NlsSafe String> NEW_LINES = Set.of("<br>", "</br>", "<br/>", "<p>", "</p>", "<p/>", "<pre>", "</pre>");

  public static void printDetails(@NotNull ConsoleView consoleView, @Nullable Failure failure, @Nullable String details) {
    String text = failure == null ? details : ObjectUtils.chooseNotNull(failure.getDescription(), failure.getMessage());
    if (text == null && failure != null && failure.getError() != null) {
      text = failure.getError().getMessage();
    }
    if (text == null) return;
    Notification notification = failure == null ? null : failure.getNotification();
    print(consoleView, notification, text);
  }

  public static void print(@NotNull BuildTextConsoleView consoleView, @NotNull String group, @NotNull BuildIssue buildIssue) {
    Project project = consoleView.getProject();
    Map<String, NotificationListener> listenerMap = new LinkedHashMap<>();
    for (BuildIssueQuickFix quickFix : buildIssue.getQuickFixes()) {
      listenerMap.put(quickFix.getId(), (notification, event) -> {
        BuildView buildView = findBuildView(consoleView);
        Component component = buildView == null ? consoleView : buildView;
        DataContext dataContext = DataManager.getInstance().getDataContext(component);
        quickFix.runQuickFix(project, dataContext);
      });
    }
    NotificationListener listener = new NotificationListener.Adapter() {
      @Override
      protected void hyperlinkActivated(@NotNull Notification notification, @NotNull HyperlinkEvent event) {
        if (event.getEventType() != HyperlinkEvent.EventType.ACTIVATED) return;

        final NotificationListener notificationListener = listenerMap.get(event.getDescription());
        if (notificationListener != null) {
          notificationListener.hyperlinkUpdate(notification, event);
        }
      }
    };

    Notification notification = new Notification(group, buildIssue.getTitle(), buildIssue.getDescription(), NotificationType.WARNING)
      .setListener(listener);
    print(consoleView, notification, buildIssue.getDescription());
  }

  private static void print(@NotNull ConsoleView consoleView, @Nullable Notification notification, @NotNull String text) {
    String content = StringUtil.convertLineSeparators(text);
    while (true) {
      Matcher tagMatcher = TAG_PATTERN.matcher(content);
      if (!tagMatcher.find()) {
        consoleView.print(content, ConsoleViewContentType.ERROR_OUTPUT);
        break;
      }
      String tagStart = tagMatcher.group();
      consoleView.print(content.substring(0, tagMatcher.start()), ConsoleViewContentType.ERROR_OUTPUT);
      Matcher aMatcher = A_PATTERN.matcher(tagStart);
      if (aMatcher.matches()) {
        final String href = aMatcher.group(2);
        int linkEnd = content.indexOf(A_CLOSING, tagMatcher.end());
        if (linkEnd > 0) {
          String linkText = content.substring(tagMatcher.end(), linkEnd).replaceAll(TAG_PATTERN.pattern(), "");
          consoleView.printHyperlink(linkText, new HyperlinkInfo() {
            @Override
            public void navigate(@NotNull Project project) {
              if (notification != null && notification.getListener() != null) {
                notification.getListener().hyperlinkUpdate(
                  notification, IJSwingUtilities.createHyperlinkEvent(href, consoleView.getComponent()));
              }
            }
          });
          content = content.substring(linkEnd + A_CLOSING.length());
          continue;
        }
      }
      if (NEW_LINES.contains(tagStart)) {
        consoleView.print("\n", ConsoleViewContentType.SYSTEM_OUTPUT);
      }
      else {
        consoleView.print(content.substring(tagMatcher.start(), tagMatcher.end()), ConsoleViewContentType.ERROR_OUTPUT);
      }
      content = content.substring(tagMatcher.end());
    }

    consoleView.print("\n", ConsoleViewContentType.SYSTEM_OUTPUT);
  }

  @ApiStatus.Internal
  @NotNull
  public static String getMessageTitle(@NotNull String message) {
    message = stripHtml(message, true);
    int sepIndex = message.indexOf(". ");
    int eolIndex = message.indexOf("\n");
    if (sepIndex < 0 || sepIndex > eolIndex && eolIndex > 0) {
      sepIndex = eolIndex;
    }
    if (sepIndex > 0) {
      message = message.substring(0, sepIndex);
    }
    return StringUtil.trimEnd(message.trim(), '.');
  }

  @ApiStatus.Experimental
  @NotNull
  public static DataContext getDataContext(@NotNull Object buildId, @NotNull AbstractViewManager buildListener) {
    BuildView buildView = buildListener.getBuildView(buildId);
    return buildView != null ? new MyDelegatingDataContext(buildView) : DataContext.EMPTY_CONTEXT;
  }

  @ApiStatus.Experimental
  @NotNull
  public static DataContext getDataContext(@NotNull Object buildId, @NotNull BuildProgressListener buildListener) {
    DataContext dataContext;
    if (buildListener instanceof BuildView) {
      dataContext = new MyDelegatingDataContext((BuildView)buildListener);
    }
    else if (buildListener instanceof AbstractViewManager) {
      dataContext = getDataContext(buildId, (AbstractViewManager)buildListener);
    }
    else {
      LOG.error("BuildView or AbstractViewManager expected to obtain proper DataContext for build console quick fixes");
      dataContext = DataContext.EMPTY_CONTEXT;
    }
    return dataContext;
  }


  @Nullable
  private static BuildView findBuildView(@NotNull Component component) {
    Component parent = component;
    while ((parent = parent.getParent()) != null) {
      if (parent instanceof BuildView) {
        return (BuildView)parent;
      }
    }
    return null;
  }

  private static class MyDelegatingDataContext implements DataContext {
    private final AtomicNotNullLazyValue<DataContext> myDelegatedDataContextValue;

    private MyDelegatingDataContext(@NotNull BuildView buildView) {
      myDelegatedDataContextValue = AtomicNotNullLazyValue.createValue(() -> DataManager.getInstance().getDataContext(buildView));
    }

    @Override
    public @Nullable Object getData(@NotNull String dataId) {
      return myDelegatedDataContextValue.getValue().getData(dataId);
    }
  }
}
