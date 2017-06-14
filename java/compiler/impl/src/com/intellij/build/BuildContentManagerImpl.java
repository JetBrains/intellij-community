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
import com.intellij.openapi.wm.impl.content.ToolWindowContentUi;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import com.intellij.util.ui.UIUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Vladislav.Soroka
 */
public class BuildContentManagerImpl implements BuildContentManager {

  public static final String BUILD_VIEW_NAME = "   Build   ";

  private ToolWindow myToolWindow;
  private final List<Runnable> myPostponedRunnables = new ArrayList<>();

  public BuildContentManagerImpl(Project project) {
    init(project);
  }

  private void init(Project project) {
    final Runnable runnable = () -> {
      ToolWindow toolWindow = ToolWindowManager.getInstance(project).registerToolWindow("Build", true, ToolWindowAnchor.BOTTOM, project, true);
      toolWindow.getComponent().putClientProperty(ToolWindowContentUi.HIDE_ID_LABEL, "true");
      toolWindow.setIcon(AllIcons.Actions.Compile);
      //toolWindow.setTitle("title");
      //toolWindow.setStripeTitle("stripeTitle");
      //toolWindow.getContentManager().
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
      // todo add to preserved place 'Build' 'Pinned Build*' 'Sync' 'Pinned Sync*' 'Tasks history tab'
      contentManager.addContent(content);
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
    for(Content content: contentManager.getContents()) {
      if (content.getDisplayName().equals(tabName)) {
        contentManager.setSelectedContent(content, false);
        break;
      }
    }
  }
}
