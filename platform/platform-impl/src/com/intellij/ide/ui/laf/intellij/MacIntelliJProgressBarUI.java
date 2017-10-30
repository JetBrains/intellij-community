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
package com.intellij.ide.ui.laf.intellij;

import com.intellij.ide.ui.laf.IntelliJLaf;
import com.intellij.ide.ui.laf.darcula.ui.DarculaProgressBarUI;
import com.intellij.ui.Gray;

import javax.swing.*;
import javax.swing.plaf.ComponentUI;
import java.awt.*;

/**
 * @author Konstantin Bulenkov
 */
public class MacIntelliJProgressBarUI extends DarculaProgressBarUI {

  @SuppressWarnings({"MethodOverridesStaticMethodOfSuperclass", "UnusedDeclaration"})
  public static ComponentUI createUI(JComponent c) {
    return new MacIntelliJProgressBarUI();
  }

  @Override protected Color getRemainderColor() {
    return Gray.xD4;
  }

  @SuppressWarnings("UseJBColor")
  @Override
  protected Color getFinishedColor() {
    return IntelliJLaf.isGraphite() ? new Color(0x989a9e) : new Color(0x0089fc);
  }

  @SuppressWarnings("UseJBColor")
  @Override
  protected Color getStartColor() {
    return IntelliJLaf.isGraphite() ? Gray.xD4 : new Color(0x86c4ff);
  }

  @Override
  protected Color getEndColor() {
    return getFinishedColor();
  }
}
