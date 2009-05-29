/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.ui.speedSearch;

import com.intellij.psi.codeStyle.NameUtil;

import java.awt.event.KeyEvent;

public class SpeedSearch {
  private String myString = "";
  private boolean myEnabled;
  private NameUtil.Matcher myMatcher;

  public void type(String letter) {
    updatePattern(myString + letter);
  }

  public void backspace() {
    if (myString.length() > 0) {
      updatePattern(myString.substring(0, myString.length() - 1));
    }
  }

  public boolean shouldBeShowing(String string) {
    return string == null ||
           myString.length() == 0 || (myMatcher != null && myMatcher.matches(string));
  }

  public void process(KeyEvent e) {
    String old = myString;

    if (e.isConsumed()) return;

    if (e.getKeyCode() == KeyEvent.VK_BACK_SPACE) {
      backspace();
      e.consume();
    }
    else if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
      if (isHoldingFilter()) {
        updatePattern("");
        e.consume();
      }
    }
    else {
      final char ch = e.getKeyChar();
      if (Character.isLetterOrDigit(ch) || ch == ' ' || ch == '*' || ch == '_') {
        type(Character.toString(ch));
        e.consume();
      }
    }

    if (!old.equalsIgnoreCase(myString)) {
      update();
    }
  }

  protected void update() {

  }

  public void noHits() {
  }

  public boolean isHoldingFilter() {
    return myEnabled && myString.length() > 0;
  }

  public void setEnabled(boolean enabled) {
    myEnabled = enabled;
  }

  public void reset() {
    if (isHoldingFilter()) {
      updatePattern("");
    }

    if (myEnabled) {
      update();
    }
  }

  public String getFilter() {
    return myString;
  }

  public void updatePattern(final String string) {
    myString = string;
    try {
      myMatcher = NameUtil.buildMatcher("*" + string, 0, true, false);
    }
    catch (Exception e) {
      myMatcher = null;
    }
  }
}
