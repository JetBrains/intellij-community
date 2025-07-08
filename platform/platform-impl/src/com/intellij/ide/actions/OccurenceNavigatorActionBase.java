// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions;

import com.intellij.diagnostic.PluginException;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.OccurenceNavigator;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsActions;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.pom.Navigatable;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import com.intellij.ui.content.ContentManagerUtil;
import com.intellij.util.containers.JBIterable;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.LinkedList;

@ApiStatus.Internal
@ApiStatus.NonExtendable
public abstract class OccurenceNavigatorActionBase extends DumbAwareAction {
  private static final Logger LOG = Logger.getInstance(OccurenceNavigatorActionBase.class);

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public final void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    if (project == null) {
      return;
    }

    OccurenceNavigator navigator = getNavigator(e.getDataContext());
    if (navigator == null || !hasOccurenceToGo(navigator)) {
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
    displayOccurrencesInfoInStatusBar(project, occurenceInfo.getOccurenceNumber(), occurenceInfo.getOccurencesCount());
  }

  public static void displayOccurrencesInfoInStatusBar(Project project, int occurrenceNumber, int occurenceCount) {
    if (occurrenceNumber > 0 && occurenceCount > 0) {
      WindowManager.getInstance().getStatusBar(project).setInfo(
        IdeBundle.message("message.occurrence.N.of.M", occurrenceNumber, occurenceCount));
    }
  }

  @Override
  public void update(@NotNull AnActionEvent event) {
    Presentation presentation = event.getPresentation();
    Project project = event.getData(CommonDataKeys.PROJECT);
    if (project == null) {
      presentation.setEnabled(false);
      // make it invisible only in the main menu to avoid initial invisibility in toolbars
      presentation.setVisible(!ActionPlaces.isMainMenuOrActionSearch(event.getPlace()));
      return;
    }
    UpdateSession session = event.getUpdateSession();
    OccurenceNavigator navigator = session.compute(
      this, "getNavigator", ActionUpdateThread.EDT, () -> getNavigator(event.getDataContext()));
    if (navigator == null) {
      presentation.setEnabled(false);
      // make it invisible only in main menu to avoid initial invisibility in toolbars
      presentation.setVisible(!ActionPlaces.isMainMenuOrActionSearch(event.getPlace()));
      return;
    }
    presentation.setVisible(true);
    try {
      boolean enabled = Boolean.TRUE.equals(session.compute(
        navigator, "hasOccurenceToGo", navigator.getActionUpdateThread(), () -> hasOccurenceToGo(navigator)));
      presentation.setEnabled(enabled);
      String description = getDescription(navigator);
      if (StringUtil.isEmpty(description)) {
        LOG.error(PluginException.createByClass("Empty description provided by " + navigator.getClass().getName(), null, navigator.getClass()));
      }
      else {
        presentation.setText(description);
      }
    }
    catch (IndexNotReadyException e) {
      presentation.setEnabled(false);
    }
  }

  protected abstract OccurenceNavigator.OccurenceInfo go(OccurenceNavigator navigator);

  protected abstract boolean hasOccurenceToGo(OccurenceNavigator navigator);

  protected abstract @NlsActions.ActionText String getDescription(OccurenceNavigator navigator);

  protected @Nullable OccurenceNavigator getNavigator(DataContext dataContext) {
    ContentManager contentManager = ContentManagerUtil.getContentManagerFromContext(dataContext, false);
    if (contentManager != null) {
      Content content = contentManager.getSelectedContent();
      OccurenceNavigator navigator = content != null ? findNavigator(content.getComponent()) : null;
      if (navigator != null) {
        return navigator;
      }
    }

    return getOccurenceNavigatorFromContext(dataContext);
  }

  private static @Nullable OccurenceNavigator findNavigator(JComponent parent) {
    LinkedList<JComponent> queue = new LinkedList<>();
    queue.addLast(parent);
    while (!queue.isEmpty()) {
      JComponent component = queue.removeFirst();
      if (component instanceof OccurenceNavigator) {
        return (OccurenceNavigator)component;
      }
      if (component instanceof JTabbedPane) {
        JComponent selectedComponent = (JComponent)((JTabbedPane)component).getSelectedComponent();
        if (selectedComponent != null) {
          queue.addLast(selectedComponent);
        }
      }
      else if (component != null){
        for (int i = 0; i < component.getComponentCount(); i++) {
          Component child = component.getComponent(i);
          if (!(child instanceof JComponent)) {
            continue;
          }
          queue.addLast((JComponent)child);
        }
      }
    }
    return null;
  }

  private static @Nullable OccurenceNavigator getOccurenceNavigatorFromContext(@NotNull DataContext dataContext) {
    Project project = CommonDataKeys.PROJECT.getData(dataContext);
    Component component = PlatformCoreDataKeys.CONTEXT_COMPONENT.getData(dataContext);
    for (Component c = component; c != null; c = c.getParent()) {
      if (c instanceof OccurenceNavigator) {
        return (OccurenceNavigator)c;
      }
    }
    if (project == null) return null;

    for (ToolWindow toolWindow : JBIterable.of(PlatformDataKeys.LAST_ACTIVE_TOOL_WINDOWS.getData(dataContext))) {
      OccurenceNavigator navigator = findNavigator(toolWindow.getComponent());
      if (navigator != null) return navigator;
    }
    return null;
  }
}