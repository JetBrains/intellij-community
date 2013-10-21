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
package com.intellij.openapi.externalSystem.service.project.wizard;

import com.intellij.ide.fileTemplates.FileTemplate;
import com.intellij.ide.fileTemplates.FileTemplateManager;
import com.intellij.ide.util.projectWizard.ModuleBuilder;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.settings.AbstractExternalSystemSettings;
import com.intellij.openapi.externalSystem.settings.ExternalProjectSettings;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.externalSystem.util.ExternalSystemBundle;
import com.intellij.openapi.externalSystem.util.ExternalSystemUiUtil;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.io.IOException;

/**
 * @author Denis Zhdanov
 * @since 6/26/13 10:39 AM
 */
public abstract class AbstractExternalModuleBuilder<S extends ExternalProjectSettings> extends ModuleBuilder {

  private static final Logger LOG = Logger.getInstance("#" + AbstractExternalModuleBuilder.class.getName());

  @NotNull private final Icon                          myIcon;
  @NotNull private final ProjectSystemId               myExternalSystemId;
  @NotNull private final S                             myExternalProjectSettings;

  protected AbstractExternalModuleBuilder(@NotNull final ProjectSystemId externalSystemId,
                                          @NotNull final S externalProjectSettings)
  {
    myExternalSystemId = externalSystemId;
    myExternalProjectSettings = externalProjectSettings;
    Icon icon = ExternalSystemUiUtil.getUiAware(externalSystemId).getProjectIcon();
    myIcon = icon == null ? super.getNodeIcon() : icon;
  }

  @Override
  public String getBuilderId() {
    return getClass().getName();
  }

  @Override
  public String getPresentableName() {
    return myExternalSystemId.getReadableName();
  }

  @Override
  public String getDescription() {
    return ExternalSystemBundle.message("module.type.description", myExternalSystemId.getReadableName());
  }

  @Override
  public Icon getNodeIcon() {
    return myIcon;
  }

  @Override
  public void setupRootModel(ModifiableRootModel model) throws ConfigurationException {
    String contentPath = getContentEntryPath();
    if (StringUtil.isEmpty(contentPath)) {
      return;
    }
    File contentRootDir = new File(contentPath);
    FileUtilRt.createDirectory(contentRootDir);
    LocalFileSystem fileSystem = LocalFileSystem.getInstance();
    VirtualFile vContentRootDir = fileSystem.refreshAndFindFileByIoFile(contentRootDir);
    if (vContentRootDir == null) {
      return;
    }

    model.addContentEntry(vContentRootDir);
    model.inheritSdk();

    VirtualFile configFile = getExternalProjectConfigFile(vContentRootDir);
    final String templateConfigName = getTemplateConfigName(myExternalProjectSettings);
    if (configFile != null && templateConfigName != null) {
      FileTemplateManager manager = FileTemplateManager.getInstance();
      FileTemplate template = manager.getInternalTemplate(templateConfigName);
      try {
        VfsUtil.saveText(configFile, template.getText());
      }
      catch (IOException e) {
        LOG.warn(String.format("Unexpected exception on applying template %s config", myExternalSystemId.getReadableName()), e);
        throw new ConfigurationException(
          e.getMessage(),
          String.format("Can't apply %s template config text", myExternalSystemId.getReadableName())
        );
      }
    }

    AbstractExternalSystemSettings settings = ExternalSystemApiUtil.getSettings(model.getProject(), myExternalSystemId);
    //noinspection unchecked
    myExternalProjectSettings.setExternalProjectPath(getContentEntryPath());
    settings.linkProject(myExternalProjectSettings);
  }

  @NotNull
  public S getExternalProjectSettings() {
    return myExternalProjectSettings;
  }

  /**
   * Asks external system-specific module builder to prepare external system config file if necessary.
   *
   * @param contentRootDir  new module's content root dir
   * @return                external system config file created by the external system-specific implementation (if any);
   *                        <code>null</code> as an indication that no external system config file has been created
   */
  @Nullable
  protected abstract VirtualFile getExternalProjectConfigFile(@NotNull VirtualFile contentRootDir);

  @Nullable
  protected abstract String getTemplateConfigName(@NotNull S settings);
}
