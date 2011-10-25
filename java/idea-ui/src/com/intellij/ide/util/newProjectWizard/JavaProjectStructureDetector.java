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
package com.intellij.ide.util.newProjectWizard;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.ide.util.DelegatingProgressIndicator;
import com.intellij.ide.util.importProject.LibrariesDetectionStep;
import com.intellij.ide.util.importProject.ModuleInsight;
import com.intellij.ide.util.importProject.ModulesDetectionStep;
import com.intellij.ide.util.importProject.ProjectDescriptor;
import com.intellij.ide.util.projectWizard.ModuleWizardStep;
import com.intellij.ide.util.projectWizard.ProjectWizardStepFactory;
import com.intellij.ide.util.projectWizard.WizardContext;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

/**
 * @author nik
 */
public class JavaProjectStructureDetector extends JavaSourceRootDetector {

  @Override
  protected String getLanguageName() {
    return "Java";
  }

  @Override
  protected String getFileExtension() {
    return JavaFileType.DEFAULT_EXTENSION;
  }

  @Override
  public List<ModuleWizardStep> createWizardSteps(ProjectFromSourcesBuilder builder,
                                                  ProjectDescriptor projectDescriptor, WizardContext context,
                                                  Icon stepIcon) {
    final List<ModuleWizardStep> steps = new ArrayList<ModuleWizardStep>();
    final ModuleInsight moduleInsight = new ModuleInsight(new DelegatingProgressIndicator());
    steps.add(new LibrariesDetectionStep(this, builder, projectDescriptor, moduleInsight, stepIcon, "reference.dialogs.new.project.fromCode.page1"));
    steps.add(new ModulesDetectionStep(this, builder, projectDescriptor, moduleInsight, stepIcon, "reference.dialogs.new.project.fromCode.page2"));
    steps.add(ProjectWizardStepFactory.getInstance().createProjectJdkStep(context));
    return steps;
  }
}
