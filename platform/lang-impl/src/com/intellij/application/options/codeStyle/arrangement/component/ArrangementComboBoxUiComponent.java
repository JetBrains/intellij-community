// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.application.options.codeStyle.arrangement.component;

import com.intellij.openapi.ui.ComboBox;
import com.intellij.psi.codeStyle.arrangement.model.ArrangementAtomMatchCondition;
import com.intellij.psi.codeStyle.arrangement.model.ArrangementMatchCondition;
import com.intellij.psi.codeStyle.arrangement.std.ArrangementSettingsToken;
import com.intellij.ui.SimpleListCellRenderer;
import com.intellij.ui.components.JBList;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public final class ArrangementComboBoxUiComponent extends AbstractArrangementUiComponent {
  
  private final JComboBox<ArrangementSettingsToken> myComboBox;

  public ArrangementComboBoxUiComponent(@NotNull List<? extends ArrangementSettingsToken> tokens) {
    super(tokens);
    ArrangementSettingsToken[] tokensArray = tokens.toArray(new ArrangementSettingsToken[0]);
    Arrays.sort(tokensArray, Comparator.comparing(ArrangementSettingsToken::getRepresentationValue));
    myComboBox = new ComboBox<>(tokensArray);
    myComboBox.setRenderer(SimpleListCellRenderer.create("", ArrangementSettingsToken::getRepresentationValue));
    myComboBox.addItemListener(new ItemListener() {
      @Override
      public void itemStateChanged(ItemEvent e) {
        if (e.getStateChange() == ItemEvent.SELECTED) {
          fireStateChanged();
        }
      }
    });
    int minWidth = 0;
    JBList<ArrangementSettingsToken> dummyList = new JBList<>();
    for (int i = 0, max = myComboBox.getItemCount(); i < max; i++) {
      Component rendererComponent = myComboBox.getRenderer().getListCellRendererComponent(
        dummyList, myComboBox.getItemAt(i), i, false, true);
      minWidth = Math.max(minWidth, rendererComponent.getPreferredSize().width);
    }
    myComboBox.setPreferredSize(new Dimension(minWidth * 5 / 3, myComboBox.getPreferredSize().height));
  }

  @Override
  public @NotNull ArrangementSettingsToken getToken() {
    return (ArrangementSettingsToken)myComboBox.getSelectedItem();
  }

  @Override
  public void chooseToken(@NotNull ArrangementSettingsToken data) throws IllegalArgumentException {
    myComboBox.setSelectedItem(data); 
  }

  @Override
  public @NotNull ArrangementMatchCondition getMatchCondition() {
    ArrangementSettingsToken token = getToken();
    return new ArrangementAtomMatchCondition(token, token);
  }

  @Override
  protected JComponent doGetUiComponent() {
    return myComboBox;
  }

  @Override
  public boolean isSelected() {
    return true;
  }

  @Override
  public void setSelected(boolean selected) {
  }

  @Override
  public boolean isEnabled() {
    return myComboBox.isEnabled();
  }

  @Override
  public void setEnabled(boolean enabled) {
    myComboBox.setEnabled(enabled); 
  }

  @Override
  protected void doReset() {
  }

  @Override
  public int getBaselineToUse(int width, int height) {
    return -1;
  }

  @Override
  public void handleMouseClickOnSelected() {
    setSelected(false);
  }
}
