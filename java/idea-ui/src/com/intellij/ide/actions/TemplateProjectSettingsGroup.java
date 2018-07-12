/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

/*
 * @author max
 */
package com.intellij.ide.actions;

import com.intellij.execution.actions.EditRunConfigurationsAction;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.Presentation;

import javax.swing.*;

public class TemplateProjectSettingsGroup extends DefaultActionGroup {
  public TemplateProjectSettingsGroup() {
    setPopup(true);

    Presentation presentation = getTemplatePresentation();
    presentation.setText("Project Defaults");
    presentation.setIcon(AllIcons.General.TemplateProjectSettings);

    add(withTextAndIcon(new TemplateProjectPropertiesAction(), "Settings", AllIcons.General.TemplateProjectSettings));
    add(withTextAndIcon(new TemplateProjectStructureAction(), "Project Structure", AllIcons.General.TemplateProjectStructure));
    add(withTextAndIcon(new EditRunConfigurationsAction(), "Run Configurations", AllIcons.ToolbarDecorator.Import));
  }

  private static AnAction withTextAndIcon(AnAction action, String text, Icon icon) {
    Presentation presentation = action.getTemplatePresentation();
    presentation.setText(text);
    presentation.setIcon(icon);
    return action;
  }
}