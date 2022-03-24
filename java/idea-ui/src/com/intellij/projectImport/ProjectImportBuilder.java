// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.projectImport;

import com.intellij.ide.DataManager;
import com.intellij.ide.JavaUiBundle;
import com.intellij.ide.util.projectWizard.ProjectBuilder;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.packaging.artifacts.ModifiableArtifactModel;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;

/**
 * An extension point for importing modules from a given file
 * (see {@link com.intellij.ide.util.newProjectWizard.AddModuleWizard#initModuleWizard}).
 * @author Vladislav.Kaznacheev
 */
public abstract class ProjectImportBuilder<T> extends ProjectBuilder {
  public static final ExtensionPointName<ProjectImportBuilder<?>> EXTENSIONS_POINT_NAME = new ExtensionPointName<>("com.intellij.projectImportBuilder");

  private boolean myUpdate;
  private String myFileToImport;

  public abstract @NotNull @Nls(capitalization = Nls.Capitalization.Sentence) String getName();

  public abstract Icon getIcon();

  public @Nullable List<T> getList() {
    return null;
  }

  public abstract boolean isMarked(final T element);

  public void setList(List<T> list) throws ConfigurationException {
  }

  public abstract void setOpenProjectSettingsAfter(boolean on);

  @Override
  public List<Module> commit(@NotNull Project project, ModifiableModuleModel model, ModulesProvider modulesProvider) {
    return commit(project, model, modulesProvider, null);
  }

  public abstract @Nullable List<Module> commit(Project project, ModifiableModuleModel model, ModulesProvider modulesProvider, ModifiableArtifactModel artifactModel);

  public void setFileToImport(@NotNull String path) {
    myFileToImport = path;
  }

  public String getFileToImport() {
    return myFileToImport;
  }

  public static @Nullable Project getCurrentProject() {
    return CommonDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext());
  }

  @NlsContexts.DialogTitle
  protected String getTitle() {
    return JavaUiBundle.message("project.import.wizard.title", getName());
  }

  @Override
  public boolean isUpdate() {
    return myUpdate;
  }

  public void setUpdate(final boolean update) {
    myUpdate = update;
  }
}
