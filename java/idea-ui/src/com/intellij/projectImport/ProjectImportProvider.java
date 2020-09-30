// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.projectImport;

import com.intellij.ide.util.newProjectWizard.StepSequence;
import com.intellij.ide.util.projectWizard.ModuleWizardStep;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public abstract class ProjectImportProvider {
  public static final ExtensionPointName<ProjectImportProvider> PROJECT_IMPORT_PROVIDER = ExtensionPointName.create("com.intellij.projectImportProvider");

  protected ProjectImportBuilder myBuilder;

  protected ProjectImportProvider(ProjectImportBuilder builder) {
    myBuilder = builder;
  }

  protected ProjectImportProvider() {
    myBuilder = null;
  }

  protected ProjectImportBuilder doGetBuilder() {
    return myBuilder;
  }

  public final ProjectImportBuilder getBuilder() {
    return doGetBuilder();
  }

  @NonNls @NotNull
  public String getId(){
    return getBuilder().getName();
  }

  @NotNull
  public @Nls(capitalization = Nls.Capitalization.Sentence) String getName(){
    return getBuilder().getName();
  }

  @Nullable
  public Icon getIcon() {
    return getBuilder().getIcon();
  }

  public boolean canImport(@NotNull VirtualFile fileOrDirectory, @Nullable Project project) {
    if (fileOrDirectory.isDirectory()) {
      return true;
    }
    else {
      return canImportFromFile(fileOrDirectory);
    }
  }

  protected boolean canImportFromFile(VirtualFile file) {
    return false;
  }

  public String getPathToBeImported(VirtualFile file) {
    return getDefaultPath(file);
  }

  public static String getDefaultPath(VirtualFile file) {
    return file.isDirectory() ? file.getPath() : file.getParent().getPath();
  }

  public boolean canCreateNewProject() {
    return true;
  }

  public boolean canImportModule() {
    return true;
  }

  public void addSteps(StepSequence sequence, WizardContext context, String id) {
    ModuleWizardStep[] steps = createSteps(context);
    for (ModuleWizardStep step : steps) {
      sequence.addSpecificStep(id, step);
    }
  }

  public ModuleWizardStep[] createSteps(WizardContext context) {
    return ModuleWizardStep.EMPTY_ARRAY;
  }

  @Nullable
  @Language("HTML")
  public @Nls String getFileSample() {
    return null;
  }
}
