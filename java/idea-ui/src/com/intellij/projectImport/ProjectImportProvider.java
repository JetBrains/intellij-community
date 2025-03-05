// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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

/**
 * An extension point for 'Import Module from Existing Sources'.
 * See {@link com.intellij.ide.actions.ImportModuleAction#createImportWizard}.
 */
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

  public @NonNls @NotNull String getId(){
    return getBuilder().getName();
  }

  public @NotNull @Nls(capitalization = Nls.Capitalization.Sentence) String getName(){
    return getBuilder().getName();
  }

  public @Nullable Icon getIcon() {
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

  /**
   * Adds the {@link ModuleWizardStep}-s from {@link ProjectImportProvider#createSteps(WizardContext)} to the import wizard.
   */
  public void addSteps(@NotNull StepSequence sequence, @NotNull WizardContext context, @NotNull String id) {
    ModuleWizardStep[] steps = createSteps(context);
    for (ModuleWizardStep step : steps) {
      sequence.addSpecificStep(id, step);
    }
  }

  /**
   * Returns the {@link ModuleWizardStep}-s to be added to the import wizard.
   */
  public ModuleWizardStep[] createSteps(WizardContext context) {
    return ModuleWizardStep.EMPTY_ARRAY;
  }

  @Language("HTML")
  public @Nullable @Nls String getFileSample() {
    return null;
  }
}
