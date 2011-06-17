/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.refactoring.ui;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.util.ui.DialogUtil;
import com.intellij.util.ui.EmptyIcon;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

/**
 * @author Konstantin Bulenkov
 */
public class ComboBoxVisibilityPanel extends VisibilityPanelBase implements ShortcutProvider {
  private final JLabel myLabel;
  private ComboBoxVisibilityGroup myGroup;
  private JButton myButton;

  public ComboBoxVisibilityPanel(String name, String[] options, String[] presentableNames) {
    final Runnable callback = new Runnable() {
      public void run() {
        myEventDispatcher.getMulticaster().stateChanged(new ChangeEvent(this));
      }
    };
    setLayout(new VerticalFlowLayout(VerticalFlowLayout.TOP));
    myLabel = new JLabel(name, EmptyIcon.create(2), SwingConstants.LEFT);
    myGroup = new ComboBoxVisibilityGroup(options, presentableNames, callback) {
      @Override
      public void actionPerformed(AnActionEvent e) {
        if (myButton != null) {
          myButton.doClick();
        }
      }
    };

    add(myLabel);
    final JComponent panel = myGroup.createCustomComponent(myGroup.getTemplatePresentation());
    add(panel);
    myButton = UIUtil.findComponentOfType(panel, JButton.class);
    DialogUtil.registerMnemonic(myLabel, this);
  }

  public ComboBoxVisibilityPanel(String name, String[] options) {
    this(name, options, options);
  }

  public ComboBoxVisibilityPanel(String[] options) {
    this(RefactoringBundle.message("visibility.combo.title"), options);
  }

  public ComboBoxVisibilityPanel(String[] options, String[] presentableNames) {
    this(RefactoringBundle.message("visibility.combo.title"), options, presentableNames);
  }

  public void setDisplayedMnemonicIndex(int index) {
    myLabel.setDisplayedMnemonicIndex(index);
  }

  @Override
  public String getVisibility() {
    return myGroup.getValue();
  }

  public void addListener(ChangeListener listener) {
    myEventDispatcher.addListener(listener);
  }

  @Override
  public void setVisibility(String visibility) {
    myGroup.setValue(visibility);
  }

  @Override
  public void addNotify() {
    super.addNotify();
    final Shortcut shortcut = getShortcut();
    if (shortcut != null) {
      myGroup.registerCustomShortcutSet(new CustomShortcutSet(shortcut), UIUtil.getRootPane(this));
    }
  }

  @Override
  public Shortcut getShortcut() {
    final int index = myLabel.getDisplayedMnemonicIndex();
    if (0 <= index && index < myLabel.getText().length()) {
      final char ch = myLabel.getText().charAt(index);
      return KeyboardShortcut.fromString("alt " + String.valueOf(ch).toUpperCase());
    }
    return null;
  }
}
