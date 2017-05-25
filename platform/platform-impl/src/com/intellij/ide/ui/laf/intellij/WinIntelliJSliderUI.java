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
package com.intellij.ide.ui.laf.intellij;

import com.sun.java.swing.plaf.windows.WindowsSliderUI;

import javax.swing.*;
import javax.swing.plaf.ComponentUI;

// TODO: rework it. This is temporary solution to get rid of ugly basic
public class WinIntelliJSliderUI extends WindowsSliderUI {
  private WinIntelliJSliderUI() {
    super(null); // super constructor is no-op. JSlider is assigned in installUI
  }

  @SuppressWarnings("MethodOverridesStaticMethodOfSuperclass")
  public static ComponentUI createUI(JComponent b)    {
    return new WinIntelliJSliderUI();
  }
}
