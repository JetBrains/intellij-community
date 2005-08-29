/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
package com.intellij.util.ui;

import com.intellij.openapi.util.SystemInfo;

import javax.swing.*;

/**
 * Created by IntelliJ IDEA.
 * User: alex
 * Date: 25.06.2005
 * Time: 21:08:46
 * To change this template use File | Settings | File Templates.
 */
public class DialogUtil {
  public static void registerMnemonic(AbstractButton button) {
    String text = button.getText();

    if (SystemInfo.isMac) {
      final int doubleAmpPosition = text.indexOf("&&");
      if (doubleAmpPosition >= 0) {
        final String beforeAmp = text.substring(0, doubleAmpPosition);
        if (doubleAmpPosition < text.length() - 2) {
          final String afterAmp = text.substring(doubleAmpPosition + 2);

          text = beforeAmp.replaceAll("&", "") + "&" + afterAmp.replaceAll("&", "");
        }
      }
    } else {
      text = text.replaceAll("&&", "");
    }

    StringBuffer realText = new StringBuffer();
    char mnemonic = '\0';
    int index = -1;
    for (int i = 0; i < text.length(); i++) {
      char ch = text.charAt(i);
      if (ch != '&') {
        realText.append(ch);
      }
      else if (i + 1 < text.length()) {
        mnemonic = text.charAt(i + 1);
        index = realText.length();
      }
    }
    if (mnemonic != '\0') {
      button.setText(realText.toString());
      button.setMnemonic(mnemonic);
      button.setDisplayedMnemonicIndex(index);
    }
  }

  public static void registerMnemonic(JLabel label, JComponent target) {
    String text = label.getText();
    if (text != null) {
      StringBuffer realText = new StringBuffer(text.length());
      char mnemonic = '\0';
      int index = -1;
      for (int i = 0; i < text.length(); i++) {
        char ch = text.charAt(i);
        if (ch != '&') {
          realText.append(ch);
        }
        else if (i + 1 == text.length()) {
          realText.append(ch);
        } else if (text.charAt(i + 1) == ' '){
          realText.append(ch);
        }
        else {
          mnemonic = text.charAt(i + 1);
          index = realText.length();
        }
      }
      if (mnemonic != '\0') {
        label.setText(realText.toString());
        label.setDisplayedMnemonic(mnemonic);
        if (target != null) {
          label.setLabelFor(target);
        }
        label.setDisplayedMnemonicIndex(index);
      }
    }
  }

}
