/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.openapi.diff.impl.dir.actions;

import com.intellij.ide.diff.DirDiffSettings;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ComboBoxAction;
import com.intellij.openapi.diff.impl.dir.DirDiffTableModel;
import com.intellij.openapi.project.DumbAware;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.ArrayList;

/**
 * @author Konstantin Bulenkov
 */
public class ChangeCompareModeGroup extends ComboBoxAction implements ShortcutProvider, DumbAware {
  private final DefaultActionGroup myGroup;
  private DirDiffSettings mySettings;
  private JButton myButton;

  public ChangeCompareModeGroup(DirDiffTableModel model) {
    mySettings = model.getSettings();
    getTemplatePresentation().setText(mySettings.compareMode.getPresentableName());
    final ArrayList<ChangeCompareModeAction> actions = new ArrayList<>();
    if (model.getSettings().showCompareModes) {
      for (DirDiffSettings.CompareMode mode : DirDiffSettings.CompareMode.values()) {
        actions.add(new ChangeCompareModeAction(model, mode));
      }
    }
    else {
      getTemplatePresentation().setVisible(false);
      getTemplatePresentation().setEnabled(false);
    }
    myGroup = new DefaultActionGroup(actions.toArray(new ChangeCompareModeAction[actions.size()]));
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    myButton.doClick();
  }

  @Override
  public void update(AnActionEvent e) {
    super.update(e);
    getTemplatePresentation().setText(mySettings.compareMode.getPresentableName());
    e.getPresentation().setText(mySettings.compareMode.getPresentableName());
  }

  @Override
  public JComponent createCustomComponent(Presentation presentation) {
    final JLabel label = new JLabel("Compare by:");
    label.setDisplayedMnemonicIndex(0);
    myButton = (JButton)super.createCustomComponent(presentation).getComponent(0);
    return JBUI.Panels.simplePanel(myButton)
      .addToLeft(label)
      .withBorder(JBUI.Borders.empty(2, 6, 2, 0));
  }

  @NotNull
  @Override
  protected DefaultActionGroup createPopupActionGroup(JComponent button) {
    return myGroup;
  }

  @Override
  public ShortcutSet getShortcut() {
    return CustomShortcutSet.fromString("alt C");
  }
}
