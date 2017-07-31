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

import com.intellij.icons.AllIcons;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.impl.ToolWindowImpl;
import com.intellij.openapi.wm.impl.content.ToolWindowContentUi;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import java.beans.PropertyChangeEvent;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author Vladislav.Soroka
 */
public class BuildContentManagerImpl implements BuildContentManager {

  public static final String Build = "Build";
  public static final String Sync = "Sync";
  private static final String[] ourPresetOrder = {Build, Sync};
  private ToolWindow myToolWindow;
  private final List<Runnable> myPostponedRunnables = new ArrayList<>();

  public BuildContentManagerImpl(Project project) {
    init(project);
  }

  private void init(Project project) {
    final Runnable runnable = () -> {
      ToolWindow toolWindow = ToolWindowManager.getInstance(project)
        .registerToolWindow("Build", true, ToolWindowAnchor.BOTTOM, project, true);
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
      Content firstContent = contentManager.getContent(0);
      assert firstContent != null;
      if (!Build.equals(firstContent.getTabName())) {
        if (contentManager.getContentCount() > 1) {
          setIdLabelHidden(false);
        }
        else {
          // we are going to adjust display name, so we need to ensure tab name is not retrieved based on display name
          content.setTabName(content.getTabName());
          content.setDisplayName(Build + ": " + content.getTabName());
        }
      }
      else {
        setIdLabelHidden(true);
      }

      if (contentManager.getContentCount() == 1) {
        myToolWindow.show(null);
      }
    });
  }

  @Override
  public void removeContent(Content content) {
    ContentManager contentManager = myToolWindow.getContentManager();
    if (contentManager != null && (!contentManager.isDisposed())) {
      contentManager.removeContent(content, true);
    }
  }

  @Override
  public void setSelectedContent(Content content) {
    myToolWindow.getContentManager().setSelectedContent(content);
  }

  @Override
  public void selectContent(String tabName) {
    ContentManager contentManager = myToolWindow.getContentManager();
    for (Content content : contentManager.getContents()) {
      if (content.getDisplayName().equals(tabName)) {
        contentManager.setSelectedContent(content, false);
        break;
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
