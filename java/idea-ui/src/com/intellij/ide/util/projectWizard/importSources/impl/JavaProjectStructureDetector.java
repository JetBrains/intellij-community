// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.util.projectWizard.importSources.impl;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.ide.util.DelegatingProgressIndicator;
import com.intellij.ide.util.importProject.*;
import com.intellij.ide.util.projectWizard.ModuleWizardStep;
import com.intellij.ide.util.projectWizard.ProjectJdkStep;
import com.intellij.ide.util.projectWizard.ProjectWizardStepFactory;
import com.intellij.ide.util.projectWizard.importSources.JavaSourceRootDetectionUtil;
import com.intellij.ide.util.projectWizard.importSources.JavaSourceRootDetector;
import com.intellij.ide.util.projectWizard.importSources.ProjectFromSourcesBuilder;
import com.intellij.java.JavaBundle;
import com.intellij.util.NullableFunction;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

public final class JavaProjectStructureDetector extends JavaSourceRootDetector {

  @NotNull
  @Override
  protected @Nls(capitalization = Nls.Capitalization.Sentence) String getLanguageName() {
    return JavaBundle.message("options.java.display.name");
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
      final ModuleWizardStep jdkStep = ProjectWizardStepFactory.getInstance().createProjectJdkStep(builder.getContext());
      steps.add(jdkStep);
      if (jdkStep instanceof ProjectJdkStep) {
        ((ProjectJdkStep)jdkStep).setProjectDescriptor(projectDescriptor);
      }
    }
    return steps;
  }

  @Override
  @NotNull
  protected NullableFunction<CharSequence, String> getPackageNameFetcher() {
    return charSequence -> JavaSourceRootDetectionUtil.getPackageName(charSequence);
  }
}
