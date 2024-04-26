/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.ide.projectWizard;

import com.intellij.ide.IdeCoreBundle;
import com.intellij.ide.util.newProjectWizard.AbstractProjectWizard;
import com.intellij.ide.util.newProjectWizard.StepSequence;
import com.intellij.ide.util.projectWizard.ModuleWizardStep;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.Set;
import java.util.function.Predicate;

import static com.intellij.ide.wizard.GeneratorNewProjectWizardBuilderAdapter.NPW_PREFIX;

/**
 * @author Dmitry Avdeev
 */
public class NewProjectWizard extends AbstractProjectWizard {

  private final StepSequence mySequence = new StepSequence();

  public NewProjectWizard(@Nullable Project project, @NotNull ModulesProvider modulesProvider, @Nullable String defaultPath) {
    super(IdeCoreBundle.message(project == null ? "title.new.project" : "title.add.module"), project, defaultPath);
    init(modulesProvider);
  }

  protected void init(@NotNull ModulesProvider modulesProvider) {
    JRootPane pane = getRootPane();
    if (pane != null) {
      pane.putClientProperty(UIUtil.NO_BORDER_UNDER_WINDOW_TITLE_KEY, Boolean.TRUE);
    }
    myWizardContext.setModulesProvider(modulesProvider);
    ProjectTypeStep projectTypeStep = new ProjectTypeStep(myWizardContext, this, modulesProvider);
    Disposer.register(getDisposable(), projectTypeStep);
    mySequence.addCommonStep(projectTypeStep);
    // hacky: module builder ID and module type id should start with [NPW_PREFIX], to be removed later, on migrating on new API.
    Predicate<Set<String>> predicate = strings -> !ContainerUtil.exists(strings, type -> type.startsWith(NPW_PREFIX));
    mySequence.addCommonFinishingStep(new ProjectSettingsStep(myWizardContext), predicate);
    for (ModuleWizardStep step : mySequence.getAllSteps()) {
      addStep(step);
    }
    super.init();
  }

  @Override
  public @Nullable Dimension getInitialSize() {
    return new Dimension(800, 600);
  }

  @Override
  protected @NotNull DialogStyle getStyle() {
    return DialogStyle.COMPACT;
  }

  @Nullable
  @Override
  protected String getDimensionServiceKey() {
    return "new project wizard";
  }

  @Override
  public StepSequence getSequence() {
    return mySequence;
  }
}
