/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.ide.impl;

import com.intellij.icons.AllIcons;
import com.intellij.ide.*;
import com.intellij.ide.actions.*;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.project.Project;
import com.intellij.ui.AutoScrollToSourceHandler;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author max
 */
public class CommonActionsManagerImpl extends CommonActionsManager {
  public AnAction createPrevOccurenceAction(OccurenceNavigator navigator) {
    return new PreviousOccurenceToolbarAction(navigator);
  }

  public AnAction createNextOccurenceAction(OccurenceNavigator navigator) {
    return new NextOccurenceToolbarAction(navigator);
  }

  public AnAction createExpandAllAction(TreeExpander expander) {
    return new ExpandAllToolbarAction(expander);
  }

  public AnAction createExpandAllAction(TreeExpander expander, JComponent component) {
    final ExpandAllToolbarAction expandAllToolbarAction = new ExpandAllToolbarAction(expander);
    expandAllToolbarAction.registerCustomShortcutSet(expandAllToolbarAction.getShortcutSet(), component);
    return expandAllToolbarAction;
  }

  @Override
  public AnAction createExpandAllHeaderAction(JTree tree) {
    AnAction action = createExpandAllAction(new DefaultTreeExpander(tree), tree);
    action.getTemplatePresentation().setIcon(AllIcons.General.ExpandAll);
    action.getTemplatePresentation().setHoveredIcon(AllIcons.General.ExpandAllHover);
    return action;
  }

  public AnAction createCollapseAllAction(TreeExpander expander) {
    return new CollapseAllToolbarAction(expander);
  }

  public AnAction createCollapseAllAction(TreeExpander expander, JComponent component) {
    final CollapseAllToolbarAction collapseAllToolbarAction = new CollapseAllToolbarAction(expander);
    collapseAllToolbarAction.registerCustomShortcutSet(collapseAllToolbarAction.getShortcutSet(), component);
    return collapseAllToolbarAction;
  }

  @Override
  public AnAction createCollapseAllHeaderAction(JTree tree) {
    AnAction action = createCollapseAllAction(new DefaultTreeExpander(tree), tree);
    action.getTemplatePresentation().setIcon(AllIcons.General.CollapseAll);
    action.getTemplatePresentation().setHoveredIcon(AllIcons.General.CollapseAllHover);
    return action;
  }

  public AnAction createHelpAction(String helpId) {
    return new ContextHelpAction(helpId);
  }

  public AnAction installAutoscrollToSourceHandler(Project project, JTree tree, final AutoScrollToSourceOptionProvider optionProvider) {
    AutoScrollToSourceHandler handler = new AutoScrollToSourceHandler() {
      public boolean isAutoScrollMode() {
        return optionProvider.isAutoScrollMode();
      }

      public void setAutoScrollMode(boolean state) {
        optionProvider.setAutoScrollMode(state);
      }
    };
    handler.install(tree);
    return handler.createToggleAction();
  }

  public AnAction createExportToTextFileAction(@NotNull ExporterToTextFile exporter) {
    return new ExportToTextFileToolbarAction(exporter);
  }
}
