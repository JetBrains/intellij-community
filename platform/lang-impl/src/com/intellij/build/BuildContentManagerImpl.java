// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.build;

import com.intellij.build.process.BuildProcessHandler;
import com.intellij.execution.runners.ExecutionUtil;
import com.intellij.execution.ui.BaseContentCloseListener;
import com.intellij.execution.ui.RunContentManagerImpl;
import com.intellij.icons.AllIcons;
import com.intellij.ide.startup.StartupManagerEx;
import com.intellij.lang.LangBundle;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.util.text.Strings;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.UIBundle;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import com.intellij.util.ModalityUiUtil;
import com.intellij.util.containers.MultiMap;
import kotlin.Unit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * @author Vladislav.Soroka
 */
public final class BuildContentManagerImpl implements BuildContentManager, Disposable {
  public static final Supplier<@NlsContexts.TabTitle String> BUILD_TAB_TITLE_SUPPLIER = LangBundle.messagePointer("tab.title.build");

  private static final List<Supplier<@NlsContexts.TabTitle String>> presetOrder = List.of(
    LangBundle.messagePointer("tab.title.sync"),
    BUILD_TAB_TITLE_SUPPLIER,
    LangBundle.messagePointer("tab.title.run"),
    LangBundle.messagePointer("tab.title.debug")
  );
  private static final Key<Map<Object, CloseListener>> CONTENT_CLOSE_LISTENERS = Key.create("CONTENT_CLOSE_LISTENERS");

  private final Project project;
  private final Map<Content, Pair<Icon, AtomicInteger>> liveContentsMap = new ConcurrentHashMap<>();

  public BuildContentManagerImpl(@NotNull Project project) {
    this.project = project;
  }

  @Override
  public void dispose() {
  }

  @Override
  public @NotNull ToolWindow getOrCreateToolWindow() {
    ToolWindowManager toolWindowManager = ToolWindowManager.getInstance(project);
    ToolWindow toolWindow = toolWindowManager.getToolWindow(TOOL_WINDOW_ID);
    if (toolWindow != null) {
      return toolWindow;
    }

    toolWindow = toolWindowManager.registerToolWindow(TOOL_WINDOW_ID, builder -> {
      builder.stripeTitle = UIBundle.messagePointer("tool.window.name.build");
      builder.icon = AllIcons.Toolwindows.ToolWindowBuild;
      return Unit.INSTANCE;
    });
    toolWindow.setToHideOnEmptyContent(true);
    return toolWindow;
  }

  private void invokeLaterIfNeeded(@NotNull Runnable runnable) {
    if (project.isDefault()) {
      return;
    }

    StartupManagerEx.getInstanceEx(project).runAfterOpened(() -> {
      ModalityUiUtil.invokeLaterIfNeeded(ModalityState.defaultModalityState(), project.getDisposed(), runnable);
    });
  }

  @Override
  public void addContent(@NotNull Content content) {
    invokeLaterIfNeeded(() -> {
      ContentManager contentManager = getOrCreateToolWindow().getContentManager();
      String name = content.getTabName();
      String category = Strings.trimEnd(StringUtil.split(name, " ").get(0), ':');
      int index = -1;
      for (int i = 0; i < presetOrder.size(); i++) {
        String s = presetOrder.get(i).get();
        if (s.equals(category)) {
          index = i;
          break;
        }
      }
      Content[] existingContents = contentManager.getContents();
      if (index != -1) {
        MultiMap<String, String> existingCategoriesNames = new MultiMap<>();
        for (Content existingContent : existingContents) {
          String tabName = existingContent.getTabName();
          existingCategoriesNames.putValue(Strings.trimEnd(StringUtil.split(tabName, " ").get(0), ':'), tabName);
        }

        int place = 0;
        for (int i = 0; i <= index; i++) {
          String key = presetOrder.get(i).get();
          place += existingCategoriesNames.get(key).size();
        }
        contentManager.addContent(content, place);
      }
      else {
        contentManager.addContent(content);
      }

      for (Content existingContent : existingContents) {
        existingContent.setDisplayName(existingContent.getTabName());
      }
      updateTabDisplayName(content, content.getTabName());
    });
  }

  public void updateTabDisplayName(Content content, @NlsContexts.TabTitle String tabName) {
    invokeLaterIfNeeded(() -> {
      if (!tabName.equals(content.getDisplayName())) {
        // we are going to adjust display name, so we need to ensure tab name is not retrieved based on display name
        content.setTabName(tabName);
        content.setDisplayName(tabName);
      }
    });
  }

  @Override
  public void removeContent(Content content) {
    invokeLaterIfNeeded(() -> {
      ToolWindow toolWindow = ToolWindowManager.getInstance(project).getToolWindow(TOOL_WINDOW_ID);
      ContentManager contentManager = toolWindow == null ? null : toolWindow.getContentManager();
      if (contentManager != null && !contentManager.isDisposed()) {
        contentManager.removeContent(content, true);
      }
    });
  }

  @Override
  public void setSelectedContent(@NotNull Content content,
                                 boolean requestFocus,
                                 boolean forcedFocus,
                                 boolean activate,
                                 @Nullable Runnable activationCallback) {
    invokeLaterIfNeeded(() -> {
      ToolWindow toolWindow = getOrCreateToolWindow();
      if (!toolWindow.isAvailable()) {
        return;
      }
      if (activate) {
        toolWindow.show(activationCallback);
      }
      toolWindow.getContentManager().setSelectedContent(content, requestFocus, forcedFocus, false);
    });
  }

  public void startBuildNotified(@NotNull BuildDescriptor buildDescriptor,
                                 @NotNull Content content,
                                 @Nullable BuildProcessHandler processHandler) {
    if (processHandler != null) {
      Map<Object, CloseListener> closeListenerMap = content.getUserData(CONTENT_CLOSE_LISTENERS);
      if (closeListenerMap == null) {
        closeListenerMap = new HashMap<>();
        content.putUserData(CONTENT_CLOSE_LISTENERS, closeListenerMap);
      }
      closeListenerMap.put(buildDescriptor.getId(), new CloseListener(this, content, processHandler));
    }
    Pair<Icon, AtomicInteger> pair = liveContentsMap.computeIfAbsent(content, c -> new Pair<>(c.getIcon(), new AtomicInteger(0)));
    pair.second.incrementAndGet();
    content.putUserData(ToolWindow.SHOW_CONTENT_ICON, Boolean.TRUE);
    if (pair.first == null) {
      content.putUserData(Content.TAB_LABEL_ORIENTATION_KEY, ComponentOrientation.RIGHT_TO_LEFT);
    }
    content.setIcon(ExecutionUtil.getLiveIndicator(pair.first, 0, 13));
    invokeLaterIfNeeded(() -> {
      JComponent component = content.getComponent();
      component.invalidate();
      if (!liveContentsMap.isEmpty()) {
        getOrCreateToolWindow().setIcon(ExecutionUtil.getLiveIndicator(AllIcons.Toolwindows.ToolWindowBuild));
      }
    });
  }

  public void finishBuildNotified(@NotNull BuildDescriptor buildDescriptor, @NotNull Content content) {
    Map<Object, CloseListener> closeListenerMap = content.getUserData(CONTENT_CLOSE_LISTENERS);
    if (closeListenerMap != null) {
      CloseListener closeListener = closeListenerMap.remove(buildDescriptor.getId());
      if (closeListener != null) {
        Disposer.dispose(closeListener);
        if (closeListenerMap.isEmpty()) {
          content.putUserData(CONTENT_CLOSE_LISTENERS, null);
        }
      }
    }

    Pair<Icon, AtomicInteger> pair = liveContentsMap.get(content);
    if (pair != null && pair.second.decrementAndGet() == 0) {
      content.setIcon(pair.first);
      if (pair.first == null) {
        content.putUserData(ToolWindow.SHOW_CONTENT_ICON, Boolean.FALSE);
      }
      liveContentsMap.remove(content);
    }

    invokeLaterIfNeeded(() -> {
      if (liveContentsMap.isEmpty()) {
        getOrCreateToolWindow().setIcon(AllIcons.Toolwindows.ToolWindowBuild);
      }
    });
  }

  private static final class CloseListener extends BaseContentCloseListener {
    private @Nullable BuildProcessHandler myProcessHandler;

    private CloseListener(@NotNull BuildContentManagerImpl buildContentManager, @NotNull Content content, @NotNull BuildProcessHandler processHandler) {
      super(content, buildContentManager.project, buildContentManager);

      myProcessHandler = processHandler;
    }

    @Override
    protected void disposeContent(@NotNull Content content) {
      if (myProcessHandler instanceof Disposable) {
        Disposer.dispose((Disposable)myProcessHandler);
      }
      myProcessHandler = null;
    }

    @Override
    protected boolean closeQuery(@NotNull Content content, boolean modal) {
      if (myProcessHandler == null || myProcessHandler.isProcessTerminated() || myProcessHandler.isProcessTerminating()) {
        return true;
      }

      myProcessHandler.putUserData(RunContentManagerImpl.ALWAYS_USE_DEFAULT_STOPPING_BEHAVIOUR_KEY, Boolean.TRUE);
      final String sessionName = myProcessHandler.getExecutionName();
      final WaitForProcessTask task = new WaitForProcessTask(myProcessHandler, sessionName, modal, myProject) {
        @Override
        public void onCancel() {
          // stop waiting for the process
          myProcessHandler.forceProcessDetach();
        }
      };
      return askUserAndWait(myProcessHandler, sessionName, task);
    }
  }
}
