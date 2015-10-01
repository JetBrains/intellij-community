/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.ui.speedSearch;

import com.intellij.psi.codeStyle.NameUtil;
import com.intellij.util.text.Matcher;
import org.jetbrains.annotations.Nullable;

import java.awt.event.KeyEvent;

public class SpeedSearch {
  private String myString = "";
  private boolean myEnabled;
  private Matcher myMatcher;

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
      if (Character.isLetterOrDigit(ch) || ch == ' ' || ch == '*' || ch == '_' || ch == '-' || ch == '"' || ch == '\'' || ch == '/' || ch == '.' || ch == '$') {
        type(Character.toString(ch));
        e.consume();
      }
    }

    if (!old.equalsIgnoreCase(myString)) {
      update();
    }
  }

  public void update() {

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

  @Nullable
  public Matcher getMatcher() {
    return myMatcher;
  }
}
