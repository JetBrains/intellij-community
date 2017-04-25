/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.ui.components;

import javax.swing.*;

/**
 * @author Konstantin Bulenkov
 */
public class OnOffButton extends JToggleButton {
  private final static String myOnText = "ON";
  private final static String myOffText = "OFF";

  public OnOffButton() {
    setBorder(null);
    setOpaque(false);
  }

  public String getOnText() {
    return myOnText;
  }

  //public void setOnText(String onText) {
  //  myOnText = onText;
  //}

  public String getOffText() {
    return myOffText;
  }

  //public void setOffText(String offText) {
  //  myOffText = offText;
  //}

  @Override public String getUIClassID() {
    return "OnOffButtonUI";
  }
}
