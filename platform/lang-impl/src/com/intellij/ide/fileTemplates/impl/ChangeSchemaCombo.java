/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.ide.fileTemplates.impl;

import com.intellij.ide.fileTemplates.FileTemplatesScheme;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.ex.ComboBoxAction;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.Condition;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author Dmitry Avdeev
 */
public class ChangeSchemaCombo extends ComboBoxAction implements DumbAware {

  private final AllFileTemplatesConfigurable myConfigurable;

  public ChangeSchemaCombo(AllFileTemplatesConfigurable configurable) {
    myConfigurable = configurable;
  }

  @Override
  public void update(AnActionEvent e) {
    super.update(e);
    e.getPresentation().setText(myConfigurable.getCurrentScheme().getName());
  }

  @NotNull
  @Override
  protected DefaultActionGroup createPopupActionGroup(JComponent button) {
    DefaultActionGroup group = new DefaultActionGroup(new ChangeSchemaAction(FileTemplatesScheme.DEFAULT));
    FileTemplatesScheme scheme = myConfigurable.getManager().getProjectScheme();
    if (scheme != null) {
      group.add(new ChangeSchemaAction(scheme));
    }
    return group;
  }

  @Override
  protected Condition<AnAction> getPreselectCondition() {
    return new Condition<AnAction>() {
      @Override
      public boolean value(AnAction action) {
        return myConfigurable.getCurrentScheme().getName().equals(action.getTemplatePresentation().getText());
      }
    };
  }

  private class ChangeSchemaAction extends AnAction {

    private final FileTemplatesScheme myScheme;

    public ChangeSchemaAction(@NotNull FileTemplatesScheme scheme) {
      super(scheme.getName());
      myScheme = scheme;
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      myConfigurable.changeScheme(myScheme);
      ChangeSchemaCombo.this.getTemplatePresentation().setText(myScheme.getName());
    }
  }
}
