/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.openapi.ui.popup.util;

import com.intellij.openapi.ui.popup.MnemonicNavigationFilter;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.SpeedSearchFilter;
import com.intellij.util.ui.UIUtil;

public abstract class BaseStep<T> implements PopupStep<T>, SpeedSearchFilter<T>, MnemonicNavigationFilter<T> {

  private Runnable myFinalRunnable;

  public boolean isSpeedSearchEnabled() {
    return false;
  }

  public boolean isAutoSelectionEnabled() {
    return true;
  }

  public SpeedSearchFilter<T> getSpeedSearchFilter() {
    return this;
  }

  public boolean canBeHidden(T value) {
    return true;
  }

  public String getIndexedString(T value) {
    return getTextFor(value);
  }

  public boolean isMnemonicsNavigationEnabled() {
    return false;
  }

  public int getMnemonicPos(T value) {
    final String text = getTextFor(value);
    int i = text.indexOf("&");
    if (i < 0) {
      i = text.indexOf(UIUtil.MNEMONIC);
    }
    return i;
  }

  public MnemonicNavigationFilter<T> getMnemonicNavigationFilter() {
    return this;
  }

  public Runnable getFinalRunnable() {
    return myFinalRunnable;
  }

  public PopupStep doFinalStep(Runnable runnable) {
    myFinalRunnable = runnable;
    return FINAL_CHOICE;
  }
}
