// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.application.options.codeStyle.arrangement.component;

import com.intellij.application.options.codeStyle.arrangement.ArrangementConstants;
import com.intellij.psi.codeStyle.arrangement.model.ArrangementAtomMatchCondition;
import com.intellij.psi.codeStyle.arrangement.model.ArrangementMatchCondition;
import com.intellij.psi.codeStyle.arrangement.std.ArrangementSettingsToken;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.util.ui.GridBag;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public final class ArrangementCheckBoxUiComponent extends AbstractArrangementUiComponent {

  private final @NotNull JPanel myComponent = new JPanel(new GridBagLayout());

  private final @NotNull ArrangementAtomMatchCondition myCondition;
  private final @NotNull JBCheckBox                    myCheckBox;
  private final @NotNull JLabel                        myTextLabel;

  public ArrangementCheckBoxUiComponent(@NotNull ArrangementSettingsToken token) {
    super(token);
    myComponent.setOpaque(false);
    myCondition = new ArrangementAtomMatchCondition(token);
    myCheckBox = new JBCheckBox();
    myCheckBox.setOpaque(false);
    myTextLabel = new JLabel(token.getRepresentationValue());

    myCheckBox.addItemListener(new ItemListener() {
      @Override
      public void itemStateChanged(ItemEvent e) {
        myTextLabel.setEnabled(myCheckBox.isEnabled());
        fireStateChanged();
      }
    });
    myTextLabel.addMouseListener(new MouseAdapter() {
      @Override
      public void mousePressed(MouseEvent e) {
        myCheckBox.setSelected(!myCheckBox.isSelected());
      }
    });

    myComponent.add(myCheckBox, new GridBag().anchor(GridBagConstraints.WEST).insets(0, 0, 0, 2));
    myComponent.add(myTextLabel, new GridBag().anchor(GridBagConstraints.WEST).insets(0, 0, 0, ArrangementConstants.HORIZONTAL_GAP));
  }

  @Override
  public @NotNull ArrangementSettingsToken getToken() {
    return myCondition.getType();
  }

  @Override
  public void chooseToken(@NotNull ArrangementSettingsToken data) throws UnsupportedOperationException {
    if (!getToken().equals(data)) {
      throw new UnsupportedOperationException(String.format(
        "Can't choose '%s' data at the check box token with data '%s'", data, getToken()
      ));
    }
  }

  @Override
  public @NotNull ArrangementMatchCondition getMatchCondition() {
    return myCondition;
  }

  @Override
  protected JComponent doGetUiComponent() {
    return myComponent;
  }

  @Override
  protected void doReset() {
  }

  @Override
  public boolean isEnabled() {
    return myCheckBox.isEnabled();
  }

  @Override
  public void setEnabled(boolean enabled) {
    myCheckBox.setEnabled(enabled); 
  }

  @Override
  public boolean isSelected() {
    return myCheckBox.isSelected();
  }

  @Override
  public void setSelected(boolean selected) {
    myCheckBox.setSelected(selected);
  }

  @Override
  public int getBaselineToUse(int width, int height) {
    return myTextLabel.getBaseline(width, height);
  }

  @Override
  public void handleMouseClickOnSelected() {
    setSelected(false);
  }
}
