// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.components;

import com.intellij.openapi.util.Weighted;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static com.intellij.openapi.util.text.StringUtil.notNullize;
import static com.intellij.util.containers.UtilKt.stream;
import static java.util.stream.Collectors.toSet;

public class JBOptionButton extends JButton implements Weighted {

  public static final String PROP_OPTIONS = "OptionActions";
  public static final String PROP_OPTION_TOOLTIP = "OptionTooltip";

  private Action[] myOptions;
  private String myOptionTooltipText;
  private final Set<OptionInfo> myOptionInfos = new HashSet<>();
  private boolean myOkToProcessDefaultMnemonics = true;

  public JBOptionButton(Action action, Action[] options) {
    super(action);
    setOptions(options);
  }

  @Override
  public String getUIClassID() {
    return "OptionButtonUI";
  }

  @Override
  public OptionButtonUI getUI() {
    return (OptionButtonUI)super.getUI();
  }

  @Override
  public double getWeight() {
    return 0.5;
  }

  public void togglePopup() {
    getUI().togglePopup();
  }

  public void showPopup(@Nullable Action actionToSelect, boolean ensureSelection) {
    getUI().showPopup(actionToSelect, ensureSelection);
  }

  public void closePopup() {
    getUI().closePopup();
  }

  @Nullable
  public Action[] getOptions() {
    return myOptions;
  }

  public void setOptions(@Nullable Action[] options) {
    Action[] oldOptions = myOptions;
    myOptions = options;

    fillOptionInfos();
    firePropertyChange(PROP_OPTIONS, oldOptions, myOptions);
    if (!Arrays.equals(oldOptions, myOptions)) {
      revalidate();
      repaint();
    }
  }

  /**
   * @deprecated Use {@link JBOptionButton#setOptions(Action[])} instead.
   */
  @Deprecated
  public void updateOptions(@Nullable Action[] options) {
    setOptions(options);
  }

  public boolean isSimpleButton() {
    return myOptions == null || myOptions.length == 0;
  }

  private void fillOptionInfos() {
    myOptionInfos.clear();
    myOptionInfos.addAll(stream(myOptions).filter(action -> action != getAction()).map(this::getMenuInfo).collect(toSet()));
  }

  public boolean isOkToProcessDefaultMnemonics() {
    return myOkToProcessDefaultMnemonics;
  }

  public static class OptionInfo {

    String myPlainText;
    int myMnemonic;
    int myMnemonicIndex;
    JBOptionButton myButton;
    Action myAction;

    OptionInfo(String plainText, int mnemonic, int mnemonicIndex, JBOptionButton button, Action action) {
      myPlainText = plainText;
      myMnemonic = mnemonic;
      myMnemonicIndex = mnemonicIndex;
      myButton = button;
      myAction = action;
    }

    public String getPlainText() {
      return myPlainText;
    }

    public int getMnemonic() {
      return myMnemonic;
    }

    public int getMnemonicIndex() {
      return myMnemonicIndex;
    }

    public JBOptionButton getButton() {
      return myButton;
    }

    public Action getAction() {
      return myAction;
    }
  }

  @NotNull
  private OptionInfo getMenuInfo(@NotNull Action each) {
    final String text = notNullize((String)each.getValue(Action.NAME));
    int mnemonic = -1;
    int mnemonicIndex = -1;
    StringBuilder plainText = new StringBuilder();
    for (int i = 0; i < text.length(); i++) {
      char ch = text.charAt(i);
      if (ch == '&' || ch == '_') {
        if (i + 1 < text.length()) {
          final char mnemonicsChar = text.charAt(i + 1);
          mnemonic = Character.toUpperCase(mnemonicsChar);
          mnemonicIndex = i;          
        }
        continue;
      }
      plainText.append(ch);
    }

    return new OptionInfo(plainText.toString(), mnemonic, mnemonicIndex, this, each);
  }

  @NotNull
  public Set<OptionInfo> getOptionInfos() {
    return myOptionInfos;
  }

  @Nullable
  public String getOptionTooltipText() {
    return myOptionTooltipText;
  }

  public void setOptionTooltipText(@Nullable String text) {
    String oldValue = myOptionTooltipText;
    myOptionTooltipText = text;
    firePropertyChange(PROP_OPTION_TOOLTIP, oldValue, myOptionTooltipText);
  }

  public void setOkToProcessDefaultMnemonics(boolean ok) {
    myOkToProcessDefaultMnemonics = ok;
  }
}
