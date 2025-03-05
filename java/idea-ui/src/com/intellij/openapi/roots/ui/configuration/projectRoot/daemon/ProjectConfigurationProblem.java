// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.ui.configuration.projectRoot.daemon;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ui.configuration.ConfigurationError;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.awt.RelativePoint;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class ProjectConfigurationProblem extends ConfigurationError {
  private final ProjectStructureProblemDescription myDescription;
  private final Project myProject;

  public ProjectConfigurationProblem(ProjectStructureProblemDescription description, Project project) {
    super(StringUtil.unescapeXmlEntities(description.getMessage(true)), computeDescription(description),
          getSettings(project, description.getProblemLevel()).isIgnored(description));
    myDescription = description;
    myProject = project;
  }

  private static ProjectStructureProblemsSettings getSettings(Project project, ProjectStructureProblemDescription.ProblemLevel problemLevel) {
    if (problemLevel == ProjectStructureProblemDescription.ProblemLevel.PROJECT) {
      return ProjectStructureProblemsSettings.getProjectInstance(project);
    }
    else {
      return ProjectStructureProblemsSettings.getGlobalInstance();
    }
  }

  private static HtmlChunk computeDescription(ProjectStructureProblemDescription description) {
    if (description.getDescription().isEmpty()) {
      return HtmlChunk.text(description.getMessage(true));
    }

    return description.getDescription();
  }

  public @NotNull ProjectStructureProblemDescription getProblemDescription() {
    return myDescription;
  }

  @Override
  public void ignore(boolean ignored) {
    super.ignore(ignored);
    getSettings(myProject, myDescription.getProblemLevel()).setIgnored(myDescription, ignored);
  }

  @Override
  public void navigate() {
    myDescription.getPlace().navigate();
  }

  @Override
  public boolean canBeFixed() {
    return !myDescription.getFixes().isEmpty();
  }

  @Override
  public void fix(final JComponent contextComponent, RelativePoint relativePoint) {
    JBPopupFactory.getInstance().createListPopup(new BaseListPopupStep<ConfigurationErrorQuickFix>(null, myDescription.getFixes()) {
      @Override
      public @NotNull String getTextFor(ConfigurationErrorQuickFix value) {
        return value.getActionName();
      }

      @Override
      public PopupStep<?> onChosen(final ConfigurationErrorQuickFix selectedValue, boolean finalChoice) {
        return doFinalStep(() -> selectedValue.performFix());
      }
    }).show(relativePoint);
  }
}
