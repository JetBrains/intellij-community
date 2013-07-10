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

import com.intellij.ide.util.projectWizard.ModuleWizardStep;
import com.intellij.openapi.externalSystem.settings.ExternalProjectSettings;
import com.intellij.openapi.externalSystem.util.ExternalSystemSettingsControl;
import com.intellij.openapi.externalSystem.util.ExternalSystemUiUtil;
import com.intellij.openapi.externalSystem.util.PaintAwarePanel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author Denis Zhdanov
 * @since 6/26/13 1:38 PM
 */
public class ExternalModuleSettingsStep<S extends ExternalProjectSettings> extends ModuleWizardStep {
  
  @NotNull private final ExternalSystemSettingsControl<S> myControl;
  
  @Nullable private PaintAwarePanel myComponent;

  public ExternalModuleSettingsStep(@NotNull ExternalSystemSettingsControl<S> control) {
    myControl = control;
  }

  @Override
  public JComponent getComponent() {
    PaintAwarePanel result = myComponent;
    if (result == null) {
      result = new PaintAwarePanel();
      myControl.fillUi(result, 0);
      ExternalSystemUiUtil.fillBottom(result);
      myComponent = result;
    }
    
    return result;
  }

  @Override
  public void updateDataModel() {
  }
}
