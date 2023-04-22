// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import com.intellij.util.ui.NamedColorUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

class ProjectStructureElementRenderer extends ColoredTreeCellRenderer {
  private final StructureConfigurableContext myContext;

  ProjectStructureElementRenderer(StructureConfigurableContext context) {
    myContext = context;
  }

  @Override
  public void customizeCellRenderer(@NotNull JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
    if (value instanceof MasterDetailsComponent.MyNode node) {
      final NamedConfigurable<?> namedConfigurable = node.getConfigurable();
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
          ((ProjectStructureElementConfigurable<?>)namedConfigurable).getProjectStructureElement();
        if (projectStructureElement != null) {
          final ProjectStructureDaemonAnalyzer daemonAnalyzer = myContext.getDaemonAnalyzer();
          final ProjectStructureProblemsHolderImpl problemsHolder = daemonAnalyzer.getProblemsHolder(projectStructureElement);
          if (problemsHolder != null && problemsHolder.containsProblems()) {
            final boolean isUnused = problemsHolder.containsProblems(ProjectStructureProblemType.Severity.UNUSED);
            final boolean haveWarnings = problemsHolder.containsProblems(ProjectStructureProblemType.Severity.WARNING);
            final boolean haveErrors = problemsHolder.containsProblems(ProjectStructureProblemType.Severity.ERROR);
            Color foreground;
            foreground = isUnused ? NamedColorUtil.getInactiveTextColor() : null;
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
