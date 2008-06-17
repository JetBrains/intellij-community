/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.ui.popup.util;

import com.intellij.openapi.ui.popup.MnemonicNavigationFilter;
import com.intellij.ui.popup.WizardPopup;

import java.awt.event.KeyEvent;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class MnemonicsSearch<T> {

  private WizardPopup myPopup;
  private Map<String, T> myChar2ValueMap = new HashMap();

  public MnemonicsSearch(WizardPopup popup) {
    myPopup = popup;
    if (!myPopup.getStep().isMnemonicsNavigationEnabled()) return;

    final MnemonicNavigationFilter filter = myPopup.getStep().getMnemonicNavigationFilter();
    final List<T> values = filter.getValues();
    for (T each : values) {
      final int pos = filter.getMnemonicPos(each);
      if (pos != -1) {
        final String text = filter.getTextFor(each);
        final String charText = text.substring(pos + 1, pos + 2);
        myChar2ValueMap.put(charText.toUpperCase(), each);
        myChar2ValueMap.put(charText.toLowerCase(), each);
      }
    }
  }

  public void process(KeyEvent e) {
    if (e.isConsumed()) return;

    if (Character.isLetterOrDigit(e.getKeyChar())) {
      final String s = Character.toString(e.getKeyChar());
      final T toSelect = myChar2ValueMap.get(s);
      if (toSelect != null) {
        select(toSelect);
        e.consume();
      }
    }
  }

  protected abstract void select(T value);

}
