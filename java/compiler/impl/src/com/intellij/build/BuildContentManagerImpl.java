/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.build;

import com.intellij.execution.runners.ExecutionUtil;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.impl.ToolWindowImpl;
import com.intellij.openapi.wm.impl.content.ToolWindowContentUi;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import com.intellij.ui.content.TabbedContent;
import com.intellij.util.ContentUtilEx;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.beans.PropertyChangeEvent;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static com.intellij.util.ContentUtilEx.getFullName;

/**
 * @author Vladislav.Soroka
 */
public class BuildContentManagerImpl implements BuildContentManager {

  public static final String Build = "Build";
  public static final String Sync = "Sync";
  private static final String[] ourPresetOrder = {Build, Sync};
  private Project myProject;
  private ToolWindow myToolWindow;
  private final List<Runnable> myPostponedRunnables = new ArrayList<>();
  private Map<Content, Pair<Icon, AtomicInteger>> liveContentsMap = ContainerUtil.newConcurrentMap();

  public BuildContentManagerImpl(Project project) {
    init(project);
  }

  private void init(Project project) {
    myProject = project;
    final Runnable runnable = () -> {
      ToolWindow toolWindow = ToolWindowManager.getInstance(project)
        .registerToolWindow(ToolWindowId.BUILD, true, ToolWindowAnchor.BOTTOM, project, true);
      toolWindow.getComponent().putClientProperty(ToolWindowContentUi.HIDE_ID_LABEL, "true");
      toolWindow.setIcon(AllIcons.Actions.Compile);
      myToolWindow = toolWindow;
      for (Runnable postponedRunnable : myPostponedRunnables) {
        postponedRunnable.run();
      }
      myPostponedRunnables.clear();
    };

    if (project.isInitialized()) {
      UIUtil.invokeLaterIfNeeded(runnable);
    }
    else {
      StartupManager.getInstance(project).registerPostStartupActivity(runnable);
    }
  }

  public void runWhenInitialized(final Runnable runnable) {
    if (myToolWindow != null) {
      runnable.run();
    }
    else {
      myPostponedRunnables.add(runnable);
    }
  }

  @Override
  public void addContent(Content content) {
    runWhenInitialized(() -> {
      ContentManager contentManager = myToolWindow.getContentManager();
      final String name = content.getTabName();
      int idx = -1;
      for (int i = 0; i < ourPresetOrder.length; i++) {
        final String s = ourPresetOrder[i];
        if (s.equals(name)) {
          idx = i;
        }
      }
      final Content[] existingContents = contentManager.getContents();
      if (idx != -1) {
        final Set<String> existingTabNames = new HashSet<>();
        for (Content existingContent : existingContents) {
          existingTabNames.add(existingContent.getTabName());
        }
        int place = idx;
        for (int i = 0; i < idx; i++) {
          if (!existingTabNames.contains(ourPresetOrder[i])) {
            --place;
          }
        }
        contentManager.addContent(content, place);
      }
      else {
        contentManager.addContent(content);
      }

      for (Content existingContent : existingContents) {
        existingContent.setDisplayName(existingContent.getTabName());
      }
      String tabName = content.getTabName();
      updateTabDisplayName(content, tabName);
    });
  }

  public void updateTabDisplayName(Content content, String tabName) {
    String displayName;
    ContentManager contentManager = myToolWindow.getContentManager();
    Content firstContent = contentManager.getContent(0);
    assert firstContent != null;
    if (!Build.equals(firstContent.getTabName())) {
      if (contentManager.getContentCount() > 1) {
        setIdLabelHidden(false);
        displayName = tabName;
      }
      else {
        displayName = Build + ": " + tabName;
      }
    }
    else {
      displayName = tabName;
      setIdLabelHidden(true);
    }

    if (!displayName.equals(content.getDisplayName())) {
      // we are going to adjust display name, so we need to ensure tab name is not retrieved based on display name
      content.setTabName(tabName);
      content.setDisplayName(displayName);
    }
  }

  @Override
  public void removeContent(Content content) {
    ContentManager contentManager = myToolWindow.getContentManager();
    if (contentManager != null && (!contentManager.isDisposed())) {
      contentManager.removeContent(content, true);
    }
  }

  @Override
  public ActionCallback setSelectedContent(Content content,
                                           boolean requestFocus,
                                           boolean forcedFocus,
                                           boolean activate,
                                           Runnable activationCallback) {
    ActionCallback callback = myToolWindow.getContentManager().setSelectedContent(content, requestFocus, forcedFocus, false);
    if (activate) {
      ApplicationManager.getApplication().invokeLater(
        () -> myToolWindow.activate(activationCallback, requestFocus, requestFocus), myProject.getDisposed());
    }
    return callback;
  }

  @Override
  public Content addTabbedContent(@NotNull JComponent contentComponent,
                                  @NotNull String groupPrefix,
                                  @NotNull String tabName,
                                  @Nullable Icon icon,
                                  @Nullable Disposable childDisposable) {
    ContentManager contentManager = myToolWindow.getContentManager();
    ContentUtilEx.addTabbedContent(contentManager, contentComponent, groupPrefix, tabName, false, childDisposable);
    Content content = contentManager.findContent(getFullName(groupPrefix, tabName));
    if (icon != null) {
      TabbedContent tabbedContent = ContentUtilEx.findTabbedContent(contentManager, groupPrefix);
      if (tabbedContent != null) {
        tabbedContent.putUserData(ToolWindow.SHOW_CONTENT_ICON, Boolean.TRUE);
        tabbedContent.setIcon(icon);
      }
    }
    return content;
  }

  public void startBuildNotified(Content content) {
    Pair<Icon, AtomicInteger> pair = liveContentsMap.computeIfAbsent(content, c -> Pair.pair(c.getIcon(), new AtomicInteger(0)));
    pair.second.incrementAndGet();
    content.putUserData(ToolWindow.SHOW_CONTENT_ICON, Boolean.TRUE);
    content.setIcon(ExecutionUtil.getLiveIndicator(pair.first));
    JComponent component = content.getComponent();
    if (component != null) {
      component.invalidate();
    }
    myToolWindow.setIcon(ExecutionUtil.getLiveIndicator(AllIcons.Actions.Compile));
  }

  public void finishBuildNotified(Content content) {
    Pair<Icon, AtomicInteger> pair = liveContentsMap.get(content);
    if (pair != null && pair.second.decrementAndGet() == 0) {
      content.setIcon(pair.first);
      if (pair.first == null) {
        content.putUserData(ToolWindow.SHOW_CONTENT_ICON, Boolean.FALSE);
      }
      liveContentsMap.remove(content);
      if (liveContentsMap.isEmpty()) {
        myToolWindow.setIcon(AllIcons.Actions.Compile);
      }
    }
  }

  private void setIdLabelHidden(boolean hide) {
    JComponent component = myToolWindow.getComponent();
    Object oldValue = component.getClientProperty(ToolWindowContentUi.HIDE_ID_LABEL);
    Object newValue = hide ? "true" : null;
    component.putClientProperty(ToolWindowContentUi.HIDE_ID_LABEL, newValue);
    if (myToolWindow instanceof ToolWindowImpl) {
      ((ToolWindowImpl)myToolWindow).getContentUI()
        .propertyChange(new PropertyChangeEvent(this, ToolWindowContentUi.HIDE_ID_LABEL, oldValue, newValue));
    }
  }
}
