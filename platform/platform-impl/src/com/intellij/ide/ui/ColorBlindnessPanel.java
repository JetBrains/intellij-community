// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui;

import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.ui.UIBundle;
import com.intellij.ui.components.labels.SwingActionLink;
import com.intellij.ui.components.panels.HorizontalLayout;
import com.intellij.ui.scale.JBUIScale;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.event.ActionEvent;

/**
 * @author Sergey.Malenkov
 */
final class ColorBlindnessPanel extends JPanel implements ChangeListener {
  private final JCheckBox myCheckBox = new JCheckBox();
  private final JComboBox myComboBox = new ComboBox();
  private ColorBlindness myBlindness;

  ColorBlindnessPanel() {
    super(new HorizontalLayout(JBUIScale.scale(10)));
    add(HorizontalLayout.LEFT, myCheckBox);
    add(HorizontalLayout.LEFT, myComboBox);

    JLabel label = new SwingActionLink(new AbstractAction(UIBundle.message("color.blindness.link.to.help")) {
      @Override
      public void actionPerformed(ActionEvent event) {
        HelpManager.getInstance().invokeHelp("Colorblind_Settings");
      }
    });
    add(HorizontalLayout.LEFT, label);

    myCheckBox.setSelected(false);
    myCheckBox.addChangeListener(this);
    int count = 0;
    for (ColorBlindness blindness : ColorBlindness.values()) {
      ColorBlindnessSupport support = ColorBlindnessSupport.get(blindness);
      if (support != null) {
        myComboBox.addItem(new Item(blindness));
        count++;
      }
    }
    myComboBox.setEnabled(false);
    myComboBox.setVisible(count > 1);
    myCheckBox.setText(UIBundle.message(count > 1
                                        ? "color.blindness.combobox.text"
                                        : "color.blindness.checkbox.text"));
    setVisible(count > 0);
  }

  @Override
  public void stateChanged(ChangeEvent event) {
    myComboBox.setEnabled(myCheckBox.isSelected());
  }

  public ColorBlindness getColorBlindness() {
    if (myCheckBox.isSelected()) {
      if (myBlindness != null) {
        return myBlindness;
      }
      Object object = myComboBox.getSelectedItem();
      if (object instanceof Item) {
        Item item = (Item)object;
        return item.myBlindness;
      }
    }
    return null;
  }

  public void setColorBlindness(ColorBlindness blindness) {
    // invisible combobox should not be used to store values
    myBlindness = myComboBox.isVisible() ? null : blindness;
    Item item = null;
    if (myBlindness == null && blindness != null) {
      int count = myComboBox.getItemCount();
      for (int i = 0; i < count && item == null; i++) {
        Object object = myComboBox.getItemAt(i);
        if (object instanceof Item) {
          item = (Item)object;
          if (item.myBlindness != blindness) {
            item = null;
          }
        }
      }
    }
    myCheckBox.setSelected(myBlindness != null || item != null);
    if (item != null) {
      myComboBox.setSelectedItem(item);
    }
  }

  private static final class Item {
    private final ColorBlindness myBlindness;
    private final String myName;

    private Item(ColorBlindness blindness) {
      myBlindness = blindness;
      myName = UIBundle.message(blindness.key);
    }

    @Override
    public String toString() {
      return myName;
    }
  }
}
