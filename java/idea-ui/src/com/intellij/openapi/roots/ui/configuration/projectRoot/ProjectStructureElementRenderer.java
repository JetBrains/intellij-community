/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.openapi.roots.ui.configuration.projectRoot;

import com.intellij.openapi.roots.ui.configuration.projectRoot.daemon.ProjectStructureDaemonAnalyzer;
import com.intellij.openapi.roots.ui.configuration.projectRoot.daemon.ProjectStructureElement;
import com.intellij.openapi.roots.ui.configuration.projectRoot.daemon.ProjectStructureProblemType;
import com.intellij.openapi.roots.ui.configuration.projectRoot.daemon.ProjectStructureProblemsHolderImpl;
import com.intellij.openapi.ui.MasterDetailsComponent;
import com.intellij.openapi.ui.NamedConfigurable;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.JBColor;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import java.awt.*;

/**
* @author nik
*/
class ProjectStructureElementRenderer extends ColoredTreeCellRenderer {
  private final StructureConfigurableContext myContext;

  public ProjectStructureElementRenderer(StructureConfigurableContext context) {
    myContext = context;
  }

  @Override
  public void customizeCellRenderer(JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
    if (value instanceof MasterDetailsComponent.MyNode) {
      final MasterDetailsComponent.MyNode node = (MasterDetailsComponent.MyNode)value;

      final NamedConfigurable namedConfigurable = node.getConfigurable();
      if (namedConfigurable == null) {
        return;
      }

      final String displayName = node.getDisplayName();
      final Icon icon = namedConfigurable.getIcon(expanded);
      setIcon(icon);
      setToolTipText(null);
      setFont(UIUtil.getTreeFont());

      SimpleTextAttributes textAttributes = SimpleTextAttributes.REGULAR_ATTRIBUTES;
      if (node.isDisplayInBold()) {
        textAttributes = SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES;
      }
      else if (namedConfigurable instanceof ProjectStructureElementConfigurable) {
        final ProjectStructureElement projectStructureElement =
          ((ProjectStructureElementConfigurable)namedConfigurable).getProjectStructureElement();
        if (projectStructureElement != null) {
          final ProjectStructureDaemonAnalyzer daemonAnalyzer = myContext.getDaemonAnalyzer();
          final ProjectStructureProblemsHolderImpl problemsHolder = daemonAnalyzer.getProblemsHolder(projectStructureElement);
          if (problemsHolder != null && problemsHolder.containsProblems()) {
            final boolean isUnused = problemsHolder.containsProblems(ProjectStructureProblemType.Severity.UNUSED);
            final boolean haveWarnings = problemsHolder.containsProblems(ProjectStructureProblemType.Severity.WARNING);
            final boolean haveErrors = problemsHolder.containsProblems(ProjectStructureProblemType.Severity.ERROR);
            Color foreground = isUnused ? UIUtil.getInactiveTextColor() : null;
            final int style = haveWarnings || haveErrors ? SimpleTextAttributes.STYLE_WAVED : -1;
            final Color waveColor = haveErrors ? JBColor.RED : haveWarnings ? JBColor.GRAY : null;
            textAttributes = textAttributes.derive(style, foreground, null, waveColor);
            setToolTipText(problemsHolder.composeTooltipMessage());
          }

          append(displayName, textAttributes);
          String description = projectStructureElement.getDescription();
          if (description != null) {
            append(" (" + description + ")", SimpleTextAttributes.GRAY_ATTRIBUTES, false);
          }
          return;
        }
      }
      append(displayName, textAttributes);
    }
  }
}
