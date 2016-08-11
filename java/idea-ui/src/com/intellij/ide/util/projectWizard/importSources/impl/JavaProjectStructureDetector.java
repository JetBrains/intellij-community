/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.ide.util.projectWizard.importSources.impl;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.ide.util.DelegatingProgressIndicator;
import com.intellij.ide.util.importProject.*;
import com.intellij.ide.util.projectWizard.ModuleWizardStep;
import com.intellij.ide.util.projectWizard.ProjectWizardStepFactory;
import com.intellij.ide.util.projectWizard.importSources.JavaSourceRootDetectionUtil;
import com.intellij.ide.util.projectWizard.importSources.JavaSourceRootDetector;
import com.intellij.ide.util.projectWizard.importSources.ProjectFromSourcesBuilder;
import com.intellij.util.NullableFunction;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

/**
 * @author nik
 */
public class JavaProjectStructureDetector extends JavaSourceRootDetector {

  @NotNull
  @Override
  protected String getLanguageName() {
    return "Java";
  }

  @NotNull
  @Override
  protected String getFileExtension() {
    return JavaFileType.DEFAULT_EXTENSION;
  }

  @Override
  public List<ModuleWizardStep> createWizardSteps(ProjectFromSourcesBuilder builder,
                                                  ProjectDescriptor projectDescriptor,
                                                  Icon stepIcon) {
    final List<ModuleWizardStep> steps = new ArrayList<>();
    final ModuleInsight moduleInsight = new JavaModuleInsight(new DelegatingProgressIndicator(), builder.getExistingModuleNames(), builder.getExistingProjectLibraryNames());
    steps.add(new LibrariesDetectionStep(builder, projectDescriptor, moduleInsight, stepIcon, "reference.dialogs.new.project.fromCode.page1"));
    steps.add(new ModulesDetectionStep(this, builder, projectDescriptor, moduleInsight, stepIcon, "reference.dialogs.new.project.fromCode.page2"));
    if (builder.getContext().isCreatingNewProject()) {
      steps.add(ProjectWizardStepFactory.getInstance().createProjectJdkStep(builder.getContext()));
    }
    return steps;
  }

  @NotNull
  protected NullableFunction<CharSequence, String> getPackageNameFetcher() {
    return charSequence -> JavaSourceRootDetectionUtil.getPackageName(charSequence);
  }
}
