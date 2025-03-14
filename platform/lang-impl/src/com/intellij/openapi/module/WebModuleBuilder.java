// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.module;

import com.intellij.icons.AllIcons;
import com.intellij.ide.util.projectWizard.*;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.ProjectGeneratorPeer;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
* @author Dmitry Avdeev
*/
public class WebModuleBuilder<T> extends ModuleBuilder {
  public static final String GROUP_NAME = "JavaScript";
  public static final Icon ICON = AllIcons.Nodes.PpWeb;

  protected final WebProjectTemplate<T> myTemplate;
  protected final NotNullLazyValue<ProjectGeneratorPeer<T>> myGeneratorPeerLazyValue;

  public WebModuleBuilder(@NotNull WebProjectTemplate<T> template) {
    this(template, template.createLazyPeer());
  }

  public WebModuleBuilder(@NotNull WebProjectTemplate<T> template,
                          @NotNull NotNullLazyValue<ProjectGeneratorPeer<T>> lazyPeer) {
    myTemplate = template;
    myGeneratorPeerLazyValue = lazyPeer;
  }

  public WebModuleBuilder() {
    myTemplate = null;
    myGeneratorPeerLazyValue = null;
  }

  @Override
  public void setupRootModel(@NotNull ModifiableRootModel modifiableRootModel) throws ConfigurationException {
    doAddContentEntry(modifiableRootModel);
  }

  @Override
  public ModuleType<?> getModuleType() {
    return WebModuleTypeBase.getInstance();
  }

  @Override
  public boolean isTemplateBased() {
    return true;
  }

  @Override
  public String getGroupName() {
    return GROUP_NAME;
  }

  @Override
  public boolean isAvailable() {
    return false;
  }

  @Override
  public Icon getNodeIcon() {
    return myTemplate != null ? myTemplate.getIcon() : ICON;
  }

  @Override
  public @Nullable Module commitModule(@NotNull Project project, @Nullable ModifiableModuleModel model) {
    Module module = super.commitModule(project, model);
    if (module != null && myTemplate != null) {
      VirtualFile projectDir = getModuleDir(module);
      // The StartupManager#runAfterOpened callback will be skipped, in case of attaching to the multi-project workspace.
      // @see com.intellij.ide.util.projectWizard.ProjectBuilder#postCommit for more details
      StartupManager.getInstance(project).runAfterOpened(
        () -> generateProject(module, projectDir)
      );
    }
    return module;
  }

  @Override
  @ApiStatus.Internal
  public void postCommit(@NotNull Project project, @NotNull VirtualFile projectDir) {
    Module module = ModuleUtilCore.findModuleForFile(projectDir, project);
    if (module != null && myTemplate != null) {
      generateProject(module, projectDir);
    }
  }

  private void generateProject(@NotNull Module module, @NotNull VirtualFile projectDir) {
    var project = module.getProject();
    var settings = myGeneratorPeerLazyValue.getValue().getSettings();
    myTemplate.generateProject(project, projectDir, settings, module);
  }

  private static @NotNull VirtualFile getModuleDir(@NotNull Module module) {
    ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(module);
    VirtualFile dir = ProjectUtil.guessModuleDir(module);
    if (dir == null) {
      dir = ArrayUtil.getFirstElement(moduleRootManager.getContentRoots());
    }
    assert dir != null : module.getProject();
    return dir;
  }

  @Override
  public @Nullable ModuleWizardStep modifySettingsStep(@NotNull SettingsStep settingsStep) {
    if (myTemplate == null) {
      return super.modifySettingsStep(settingsStep);
    }
    myGeneratorPeerLazyValue.getValue().buildUI(settingsStep);
    return new ModuleWizardStep() {
      @Override
      public JComponent getComponent() {
        return null;
      }

      @Override
      public void updateDataModel() { }

      @Override
      public boolean validate() throws ConfigurationException {
        ValidationInfo info = myGeneratorPeerLazyValue.getValue().validate();
        if (info != null) throw new ConfigurationException(info.message);
        return true;
      }
    };
  }
}
