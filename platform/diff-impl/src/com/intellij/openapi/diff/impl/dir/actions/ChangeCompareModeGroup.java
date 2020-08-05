// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.diff.impl.dir.actions;

import com.intellij.ide.diff.DirDiffSettings;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ComboBoxAction;
import com.intellij.openapi.diff.DiffBundle;
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
  private final DirDiffSettings mySettings;
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
      getTemplatePresentation().setEnabledAndVisible(false);
    }
    myGroup = new DefaultActionGroup(actions.toArray(new ChangeCompareModeAction[0]));
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    myButton.doClick();
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    super.update(e);
    getTemplatePresentation().setText(mySettings.compareMode.getPresentableName());
    e.getPresentation().setText(mySettings.compareMode.getPresentableName());
    e.getPresentation().setEnabledAndVisible(mySettings.showCompareModes);
  }

  @NotNull
  @Override
  public JComponent createCustomComponent(@NotNull Presentation presentation, @NotNull String place) {
    final JLabel label = new JLabel(DiffBundle.message("compare.by"));
    label.setDisplayedMnemonicIndex(0);
    myButton = (JButton)super.createCustomComponent(presentation, place).getComponent(0);
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
