
/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.ide.actions;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.OccurenceNavigator;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.wm.ex.ToolWindowManagerEx;
import com.intellij.openapi.wm.ex.WindowManagerEx;
import com.intellij.pom.Navigatable;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import com.intellij.ui.content.ContentManagerUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.LinkedList;

abstract class OccurenceNavigatorActionBase extends AnAction implements DumbAware {
  public void actionPerformed(AnActionEvent e) {
    Project project = e.getProject();
    if (project == null) return;

    OccurenceNavigator navigator = getNavigator(e.getDataContext());
    if (navigator == null) {
      return;
    }
    if (!hasOccurenceToGo(navigator)) {
      return;
    }
    OccurenceNavigator.OccurenceInfo occurenceInfo = go(navigator);
    if (occurenceInfo == null) {
      return;
    }
    Navigatable descriptor = occurenceInfo.getNavigateable();
    if (descriptor != null && descriptor.canNavigate()) {
      descriptor.navigate(true);
    }
    if(occurenceInfo.getOccurenceNumber()==-1||occurenceInfo.getOccurencesCount()==-1){
      return;
    }
    WindowManager.getInstance().getStatusBar(project).setInfo(
      IdeBundle.message("message.occurrence.N.of.M", occurenceInfo.getOccurenceNumber(), occurenceInfo.getOccurencesCount()));
  }

  public void update(AnActionEvent event) {
    Presentation presentation = event.getPresentation();
    Project project = event.getData(CommonDataKeys.PROJECT);
    if (project == null) {
      presentation.setEnabled(false);
      // make it invisible only in main menu to avoid initial invisibility in toolbars
      presentation.setVisible(!ActionPlaces.isMainMenuOrActionSearch(event.getPlace()));
      return;
    }
    OccurenceNavigator navigator = getNavigator(event.getDataContext());
    if (navigator == null) {
      presentation.setEnabled(false);
      // make it invisible only in main menu to avoid initial invisibility in toolbars
      presentation.setVisible(!ActionPlaces.isMainMenuOrActionSearch(event.getPlace()));
      return;
    }
    presentation.setVisible(true);
    try {
      presentation.setEnabled(hasOccurenceToGo(navigator));
      presentation.setText(getDescription(navigator));
    }
    catch (IndexNotReadyException e) {
      presentation.setEnabled(false);
    }
  }

  protected abstract OccurenceNavigator.OccurenceInfo go(OccurenceNavigator navigator);

  protected abstract boolean hasOccurenceToGo(OccurenceNavigator navigator);

  protected abstract String getDescription(OccurenceNavigator navigator);

  @Nullable
  protected OccurenceNavigator getNavigator(DataContext dataContext) {
    ContentManager contentManager = ContentManagerUtil.getContentManagerFromContext(dataContext, false);
    if (contentManager != null) {
      Content content = contentManager.getSelectedContent();
      if (content == null) return null;
      JComponent component = content.getComponent();
      return findNavigator(component);
    }

    return (OccurenceNavigator)getOccurenceNavigatorFromContext(dataContext);
  }

  @Nullable
  private static OccurenceNavigator findNavigator(JComponent parent) {
    LinkedList<JComponent> queue = new LinkedList<>();
    queue.addLast(parent);
    while (!queue.isEmpty()) {
      JComponent component = queue.removeFirst();
      if (component instanceof OccurenceNavigator) return (OccurenceNavigator)component;
      if (component instanceof JTabbedPane) {
        final JComponent selectedComponent = (JComponent)((JTabbedPane)component).getSelectedComponent();
        if (selectedComponent != null) {
          queue.addLast(selectedComponent);
        }
      }
      else if (component != null){
        for (int i = 0; i < component.getComponentCount(); i++) {
          Component child = component.getComponent(i);
          if (!(child instanceof JComponent)) continue;
          queue.addLast((JComponent)child);
        }
      }
    }
    return null;
  }

  @Nullable
  private static Component getOccurenceNavigatorFromContext(DataContext dataContext) {
    Window window = WindowManagerEx.getInstanceEx().getMostRecentFocusedWindow();

    if (window != null) {
      Component component = window.getFocusOwner();
      for (Component c = component; c != null; c = c.getParent()) {
        if (c instanceof OccurenceNavigator) {
          return c;
        }
      }
    }

    Project project = CommonDataKeys.PROJECT.getData(dataContext);
    if (project == null) {
      return null;
    }

    ToolWindowManagerEx mgr = ToolWindowManagerEx.getInstanceEx(project);

    String id = mgr.getLastActiveToolWindowId(component -> findNavigator(component) != null);
    if (id == null) {
      return null;
    }
    return (Component)findNavigator(mgr.getToolWindow(id).getComponent());
  }

}
