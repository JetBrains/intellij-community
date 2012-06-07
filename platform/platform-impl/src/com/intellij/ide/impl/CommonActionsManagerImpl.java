/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.ide.impl;

import com.intellij.icons.AllIcons;
import com.intellij.ide.*;
import com.intellij.ide.actions.*;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.project.Project;
import com.intellij.ui.AutoScrollToSourceHandler;

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

  public AnAction createExportToTextFileAction(ExporterToTextFile exporter) {
    return new ExportToTextFileToolbarAction(exporter);
  }
}
