// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.util.projectWizard.actions;

import com.intellij.ide.util.projectWizard.ProjectSettingsStepBase;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.NlsActions.ActionText;
import com.intellij.platform.DirectoryProjectGenerator;
import org.jetbrains.annotations.NotNull;

public final class ProjectSpecificAction extends DefaultActionGroup implements DumbAware {
  public ProjectSpecificAction(final @NotNull DirectoryProjectGenerator<?> projectGenerator, final ProjectSettingsStepBase step) {
    this(projectGenerator, projectGenerator.getName(), step);
  }

  public ProjectSpecificAction(final @NotNull DirectoryProjectGenerator<?> projectGenerator,
                               final @NotNull @ActionText String name, final ProjectSettingsStepBase step) {
    super(name, true);
    getTemplatePresentation().setIcon(projectGenerator.getLogo());
    add(step);
  }
}
