// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.roots.ui.configuration.actions;

import com.intellij.ide.JavaUiBundle;
import com.intellij.ide.actions.NewProjectAction;
import com.intellij.ide.actions.NewProjectOrModuleAction;
import com.intellij.ide.projectWizard.NewProjectWizard;
import com.intellij.ide.util.newProjectWizard.AbstractProjectWizard;
import com.intellij.ide.util.projectWizard.ModuleBuilder;
import com.intellij.ide.util.projectWizard.ProjectBuilder;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ui.configuration.DefaultModulesProvider;
import com.intellij.openapi.roots.ui.configuration.ModulesConfigurator;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Eugene Zhuravlev
 */
public class NewModuleAction extends AnAction implements DumbAware, NewProjectOrModuleAction {
  public NewModuleAction() {
    super(JavaUiBundle.messagePointer("module.new.action", 0, 1), JavaUiBundle.messagePointer("module.new.action.description"), null);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    final Project project = getEventProject(e);
    if (project == null) {
      return;
    }
    Object dataFromContext = prepareDataFromContext(e);

    String defaultPath = null;
    final VirtualFile virtualFile = e.getData(CommonDataKeys.VIRTUAL_FILE);
    if (virtualFile != null && virtualFile.isDirectory()) {
      defaultPath = virtualFile.getPath();
    }
    NewProjectWizard wizard = new NewProjectWizard(project, new DefaultModulesProvider(project), defaultPath);

    if (wizard.showAndGet()) {
      createModuleFromWizard(project, dataFromContext, wizard);
    }
  }

  public @Nullable Module createModuleFromWizard(
    @NotNull Project project,
    @Nullable Object dataFromContext,
    @NotNull AbstractProjectWizard wizard
  ) {
    var builder = wizard.getBuilder(project);
    if (builder == null) return null;
    var module = builder instanceof ModuleBuilder
                 ? createModuleFromModuleBuilder(project, (ModuleBuilder)builder, dataFromContext)
                 : createModuleFromProjectBuilder(project, builder);
    project.save();
    return module;
  }

  private @Nullable Module createModuleFromModuleBuilder(
    @NotNull Project project,
    @NotNull ModuleBuilder builder,
    @Nullable Object dataFromContext
  ) {
    var module = builder.commitModule(project, null);
    if (module != null) {
      processCreatedModule(module, dataFromContext);
    }
    return module;
  }

  private static @Nullable Module createModuleFromProjectBuilder(
    @NotNull Project project,
    @NotNull ProjectBuilder builder
  ) {
    var modules = builder.commit(project, null, new DefaultModulesProvider(project));
    if (builder.isOpenProjectSettingsAfter()) {
      ModulesConfigurator.showDialog(project, null, null);
    }
    return modules == null || modules.isEmpty() ? null : modules.get(0);
  }

  @Nullable
  protected Object prepareDataFromContext(final AnActionEvent e) {
    return null;
  }

  protected void processCreatedModule(final Module module, @Nullable final Object dataFromContext) {
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    e.getPresentation().setEnabled(getEventProject(e) != null);
    NewProjectAction.updateActionText(this, e);
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @NotNull
  @Override
  public String getActionText(boolean isInNewSubmenu, boolean isInJavaIde) {
    return JavaUiBundle.message("module.new.action", isInNewSubmenu ? 1 : 0, isInJavaIde ? 1 :0);
  }
}
