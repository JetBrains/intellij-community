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

import com.intellij.build.events.BuildEvent;
import com.intellij.build.events.StartBuildEvent;
import com.intellij.execution.console.DuplexConsoleView;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.impl.ContentImpl;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author Vladislav.Soroka
 */
public class SyncViewManager implements Disposable {

  private final Project myProject;
  private final BuildContentManager myBuildContentManager;
  private Content myContent;
  private final AtomicBoolean isInitializeStarted = new AtomicBoolean();
  private final List<Runnable> myPostponedRunnables = new ArrayList<>();
  private final Map<String, BuildConsoleView> myViewProviders;
  private volatile DuplexConsoleView<BuildConsoleView, BuildConsoleView> myConsoleView;

  public SyncViewManager(Project project, BuildContentManager buildContentManager) {
    myProject = project;
    myBuildContentManager = buildContentManager;
    myViewProviders = ContainerUtil.newHashMap();
  }

  public void onEvent(BuildEvent event, String... viewIds) {
    List<Runnable> runnables = new SmartList<>();

    if (event instanceof StartBuildEvent) {
      runnables.add(() -> myConsoleView.clear());
    }

    for (String id : viewIds) {
      if ("CONSOLE".equals(id)) {
        runnables.add(() -> myConsoleView.getPrimaryConsoleView().onEvent(event));
      }
      else if ("TREE".equals(id)) {
        runnables.add(() -> myConsoleView.getSecondaryConsoleView().onEvent(event));
      }
    }
    if (myContent == null) {
      myPostponedRunnables.addAll(runnables);
      if (isInitializeStarted.compareAndSet(false, true)) {
        UIUtil.invokeLaterIfNeeded(() -> {
          final DuplexConsoleView<BuildConsoleView, BuildConsoleView> duplexConsoleView =
            new DuplexConsoleView<>(new BuildTextConsoleView(myProject, false, "CONSOLE"),
                                    new BuildTreeConsoleView(myProject));
          Disposer.register(this, duplexConsoleView);
          duplexConsoleView.setDisableSwitchConsoleActionOnProcessEnd(false);

          final DefaultActionGroup toolbarActions = new DefaultActionGroup();
          final JComponent consoleComponent = new JPanel(new BorderLayout());
          consoleComponent.add(duplexConsoleView.getComponent(), BorderLayout.CENTER);
          toolbarActions.addAll(duplexConsoleView.createConsoleActions());
          consoleComponent.add(ActionManager.getInstance().createActionToolbar(
            "", toolbarActions, false).getComponent(), BorderLayout.WEST);

          myContent = new ContentImpl(consoleComponent, "   Sync   ", true);
          myContent.setCloseable(false);
          myBuildContentManager.addContent(myContent);
          myBuildContentManager.setSelectedContent(myContent);
          myConsoleView = duplexConsoleView;

          List<Runnable> postponedRunnables = new ArrayList<>(myPostponedRunnables);
          myPostponedRunnables.clear();
          for (Runnable postponedRunnable : postponedRunnables) {
            postponedRunnable.run();
          }
        });
      }
    }
    else {
      UIUtil.invokeLaterIfNeeded(() -> {
        for (Runnable runnable : runnables) {
          runnable.run();
        }
      });
    }
  }

  @Override
  public void dispose() {
  }
}
