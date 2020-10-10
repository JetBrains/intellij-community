// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.impl;

import com.intellij.icons.AllIcons;
import com.intellij.ide.*;
import com.intellij.ide.actions.*;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.project.Project;
import com.intellij.ui.AutoScrollToSourceHandler;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

final class CommonActionsManagerImpl extends CommonActionsManager {
  @Override
  public AnAction createPrevOccurenceAction(OccurenceNavigator navigator) {
    return new PreviousOccurenceToolbarAction(navigator);
  }

  @Override
  public AnAction createNextOccurenceAction(OccurenceNavigator navigator) {
    return new NextOccurenceToolbarAction(navigator);
  }

  @Override
  public AnAction createExpandAllAction(TreeExpander expander) {
    return new ExpandAllAction(event -> expander);
  }

  @Override
  public AnAction createExpandAllAction(TreeExpander expander, JComponent component) {
    ExpandAllAction expandAllToolbarAction = new ExpandAllAction(event -> expander);
    expandAllToolbarAction.registerCustomShortcutSet(expandAllToolbarAction.getShortcutSet(), component);
    return expandAllToolbarAction;
  }

  @Override
  public AnAction createExpandAllHeaderAction(TreeExpander expander, JComponent component) {
    AnAction action = createExpandAllAction(expander, component);
    action.getTemplatePresentation().setIcon(AllIcons.Actions.Expandall);
    return action;
  }

  @Override
  public AnAction createExpandAllHeaderAction(JTree tree) {
    return createExpandAllHeaderAction(new DefaultTreeExpander(tree), tree);
  }

  @Override
  public AnAction createCollapseAllAction(TreeExpander expander) {
    return new CollapseAllAction(event -> expander);
  }

  @Override
  public AnAction createCollapseAllAction(TreeExpander expander, JComponent component) {
    CollapseAllAction collapseAllToolbarAction = new CollapseAllAction(event -> expander);
    collapseAllToolbarAction.registerCustomShortcutSet(collapseAllToolbarAction.getShortcutSet(), component);
    return collapseAllToolbarAction;
  }

  @Override
  public AnAction createCollapseAllHeaderAction(TreeExpander expander, JComponent component) {
    AnAction action = createCollapseAllAction(expander, component);
    action.getTemplatePresentation().setIcon(AllIcons.Actions.Collapseall);
    return action;
  }

  @Override
  public AnAction createCollapseAllHeaderAction(JTree tree) {
    return createCollapseAllHeaderAction(new DefaultTreeExpander(tree), tree);
  }

  @Override
  public AnAction createHelpAction(String helpId) {
    return new ContextHelpAction(helpId);
  }

  @Override
  public AnAction installAutoscrollToSourceHandler(Project project, JTree tree, final AutoScrollToSourceOptionProvider optionProvider) {
    AutoScrollToSourceHandler handler = new AutoScrollToSourceHandler() {
      @Override
      public boolean isAutoScrollMode() {
        return optionProvider.isAutoScrollMode();
      }

      @Override
      public void setAutoScrollMode(boolean state) {
        optionProvider.setAutoScrollMode(state);
      }
    };
    handler.install(tree);
    return handler.createToggleAction();
  }

  @Override
  public AnAction createExportToTextFileAction(@NotNull ExporterToTextFile exporter) {
    return new ExportToTextFileToolbarAction(exporter);
  }
}
