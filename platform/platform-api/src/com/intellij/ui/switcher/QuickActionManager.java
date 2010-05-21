/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.ui.switcher;

import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.AsyncResult;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.*;
import java.util.List;

public class QuickActionManager implements ProjectComponent {

  private SwitchProvider myActiveProvider;

  public void projectOpened() {
  }

  public void projectClosed() {
  }

  @NotNull
  public String getComponentName() {
    return "QuickActionsManager";
  }

  public void initComponent() {
  }

  public void disposeComponent() {
  }

  public static QuickActionManager getInstance(Project project) {
    return project != null ? project.getComponent(QuickActionManager.class) : null;
  }

  public void showQuickActions() {
    if (isActive()) return;

    showActionsPopup();
  }

  private void showActionsPopup() {
    DataManager.getInstance().getDataContextFromFocus().doWhenDone(new AsyncResult.Handler<DataContext>() {
      public void run(DataContext context) {
        QuickActionProvider provider = QuickActionProvider.KEY.getData(context);
        if (provider == null) return;

        List<AnAction> actions = provider.getActions(true);
        if (actions != null && actions.size() > 0) {
          DefaultActionGroup group = new DefaultActionGroup();
          for (AnAction each : actions) {
            group.add(each);
          }

          boolean firstParent = true;
          Component eachParent = provider.getComponent().getParent();
          while (eachParent != null) {
            if (eachParent instanceof QuickActionProvider) {
              QuickActionProvider eachProvider = (QuickActionProvider)eachParent;
              if (firstParent) {
                group.addSeparator();
                firstParent = false;
              }
              List<AnAction> eachActionList = eachProvider.getActions(false);
              if (eachActionList.size() > 0) {
                group.add(new Group(eachActionList, eachProvider.getName()));
              }
              if (eachProvider.isCycleRoot()) break;

            }
            eachParent = eachParent.getParent();
          }

          JBPopupFactory.getInstance()
            .createActionGroupPopup(null, group, context, JBPopupFactory.ActionSelectionAid.ALPHA_NUMBERING, true, new Runnable() {
              public void run() {
                myActiveProvider = null;
              }
            }, -1).showInFocusCenter();
        }
      }
    });

  }

  private class Group extends DefaultActionGroup {
    private String myTitle;

    private Group(List<AnAction> actions, String title) {
      setPopup(true);
      for (AnAction each : actions) {
        add(each);
      }
      myTitle = title;
    }

    @Override
    public void update(AnActionEvent e) {
      e.getPresentation().setText(myTitle);
    }
  }

  public boolean isActive() {
    return myActiveProvider != null;
  }
}
