/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.openapi.module;

import com.intellij.icons.AllIcons;
import com.intellij.ide.util.projectWizard.ModuleBuilder;
import com.intellij.ide.util.projectWizard.ModuleWizardStep;
import com.intellij.ide.util.projectWizard.SettingsStep;
import com.intellij.ide.util.projectWizard.WebProjectTemplate;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.WebProjectGenerator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
* @author Dmitry Avdeev
*         Date: 9/27/12
*/
public class WebModuleBuilder extends ModuleBuilder {

  public static final String GROUP_NAME = "Static Web";
  public static final Icon ICON = AllIcons.Nodes.PpWeb;

  private final WebProjectTemplate<?> myTemplate;

  public WebModuleBuilder(@NotNull WebProjectTemplate<?> template) {
    myTemplate = template;
  }

  public WebModuleBuilder() {
    myTemplate = null;
  }

  @Override
  public void setupRootModel(ModifiableRootModel modifiableRootModel) throws ConfigurationException {
    doAddContentEntry(modifiableRootModel);
  }

  @Override
  public ModuleType getModuleType() {
    return WebModuleType.getInstance();
  }

  @Override
  public String getPresentableName() {
    return getGroupName();
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
  public Icon getNodeIcon() {
    return myTemplate != null ? myTemplate.getIcon() : ICON;
  }

  @Nullable
  @Override
  public Module commitModule(@NotNull Project project, @Nullable ModifiableModuleModel model) {
    Module module = super.commitModule(project, model);
    if (module != null && myTemplate != null) {
      doGenerate(myTemplate, module);
    }
    return module;
  }

  private static <T> void doGenerate(@NotNull WebProjectTemplate<T> template, @NotNull Module module) {
    WebProjectGenerator.GeneratorPeer<T> peer = template.getPeer();
    ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(module);
    VirtualFile[] contentRoots = moduleRootManager.getContentRoots();
    VirtualFile dir = module.getProject().getBaseDir();
    if (contentRoots.length > 0 && contentRoots[0] != null) {
      dir = contentRoots[0];
    }
    template.generateProject(module.getProject(), dir, peer.getSettings(), module);
  }

  @Nullable
  @Override
  public ModuleWizardStep modifySettingsStep(@NotNull SettingsStep settingsStep) {
    if (myTemplate == null) {
      return super.modifySettingsStep(settingsStep);
    }
    final WebProjectGenerator.GeneratorPeer peer = myTemplate.getPeer();
    peer.buildUI(settingsStep);
    return new ModuleWizardStep() {
      @Override
      public JComponent getComponent() {
        return null;
      }

      @Override
      public void updateDataModel() {
      }

      @Override
      public boolean validate() throws ConfigurationException {
        ValidationInfo info = peer.validate();
        if (info != null) throw new ConfigurationException(info.message);
        return true;
      }
    };
  }
}
