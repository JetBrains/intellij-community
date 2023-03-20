/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

package com.intellij.ui;

import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.util.NlsContexts;

import javax.swing.*;

public class NonFocusableCheckBox extends JCheckBox {
  public NonFocusableCheckBox(@NlsContexts.Checkbox String text) {
    super(text);
    initFocusability();
  }

  public NonFocusableCheckBox() {
    initFocusability();
  }

  private void initFocusability() {
    if (!UISettings.getShadowInstance().getDisableMnemonicsInControls()) { // Or that won't be keyboard accessible at all
      setFocusable(false);
    }
  }
}
