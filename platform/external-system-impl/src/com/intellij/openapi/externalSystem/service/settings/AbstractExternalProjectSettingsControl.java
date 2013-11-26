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
package com.intellij.openapi.externalSystem.service.settings;

import com.intellij.openapi.externalSystem.settings.ExternalProjectSettings;
import com.intellij.openapi.externalSystem.util.ExternalSystemBundle;
import com.intellij.openapi.externalSystem.util.ExternalSystemSettingsControl;
import com.intellij.openapi.externalSystem.util.ExternalSystemUiUtil;
import com.intellij.openapi.externalSystem.util.PaintAwarePanel;
import com.intellij.ui.components.JBCheckBox;
import org.jetbrains.annotations.NotNull;

/**
 * Templates class for managing single external project settings (single ide project might contain multiple bindings to external
 * projects, e.g. one module is backed by a single external project and couple of others are backed by a single external multi-project).
 * 
 * @author Denis Zhdanov
 * @since 4/24/13 1:19 PM
 */
public abstract class AbstractExternalProjectSettingsControl<S extends ExternalProjectSettings>
  implements ExternalSystemSettingsControl<S>
{

  @NotNull private S myInitialSettings;

  private JBCheckBox myUseAutoImportBox;
  private JBCheckBox myCreateEmptyContentRootDirectoriesBox;
  private boolean myHideUseAutoImportBox;

  protected AbstractExternalProjectSettingsControl(@NotNull S initialSettings) {
    myInitialSettings = initialSettings;
  }

  @NotNull
  public S getInitialSettings() {
    return myInitialSettings;
  }

  public void hideUseAutoImportBox() {
    myHideUseAutoImportBox = true;
  }

  @Override
  public void fillUi(@NotNull PaintAwarePanel canvas, int indentLevel) {
    myUseAutoImportBox = new JBCheckBox(ExternalSystemBundle.message("settings.label.use.auto.import"));
    myUseAutoImportBox.setVisible(!myHideUseAutoImportBox);
    canvas.add(myUseAutoImportBox, ExternalSystemUiUtil.getFillLineConstraints(indentLevel));
    myCreateEmptyContentRootDirectoriesBox =
      new JBCheckBox(ExternalSystemBundle.message("settings.label.create.empty.content.root.directories"));
    canvas.add(myCreateEmptyContentRootDirectoriesBox, ExternalSystemUiUtil.getFillLineConstraints(indentLevel));
    fillExtraControls(canvas, indentLevel); 
  }
  
  protected abstract void fillExtraControls(@NotNull PaintAwarePanel content, int indentLevel);

  public boolean isModified() {
    return myUseAutoImportBox.isSelected() != getInitialSettings().isUseAutoImport()
           || myCreateEmptyContentRootDirectoriesBox.isSelected() != getInitialSettings().isCreateEmptyContentRootDirectories()
           || isExtraSettingModified();
  }

  protected abstract boolean isExtraSettingModified();

  public void reset() {
    reset(false);
  }

  public void reset(boolean isDefaultModuleCreation) {
    myUseAutoImportBox.setSelected(getInitialSettings().isUseAutoImport());
    myCreateEmptyContentRootDirectoriesBox.setSelected(getInitialSettings().isCreateEmptyContentRootDirectories());
    resetExtraSettings(isDefaultModuleCreation);
  }

  protected abstract void resetExtraSettings(boolean isDefaultModuleCreation);

  @Override
  public void apply(@NotNull S settings) {
    settings.setUseAutoImport(myUseAutoImportBox.isSelected());
    myInitialSettings.setUseAutoImport(myUseAutoImportBox.isSelected());
    settings.setCreateEmptyContentRootDirectories(myCreateEmptyContentRootDirectoriesBox.isSelected());
    myInitialSettings.setCreateEmptyContentRootDirectories(myCreateEmptyContentRootDirectoriesBox.isSelected());
    if (myInitialSettings.getExternalProjectPath() != null) {
      settings.setExternalProjectPath(myInitialSettings.getExternalProjectPath());
    }
    applyExtraSettings(settings);
  }

  protected abstract void applyExtraSettings(@NotNull S settings);

  public void disposeUIResources() {
    ExternalSystemUiUtil.disposeUi(this);
  }
  
  @Override
  public void showUi(boolean show) {
    ExternalSystemUiUtil.showUi(this, show);
  }
}
