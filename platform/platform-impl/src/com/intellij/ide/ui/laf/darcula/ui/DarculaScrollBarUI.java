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
package com.intellij.ide.ui.laf.darcula.ui;

import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.util.ui.ButtonlessScrollBarUI;

import javax.swing.*;
import javax.swing.plaf.ComponentUI;

import static com.intellij.util.ReflectionUtil.newInstance;

/**
 * @author Konstantin Bulenkov
 */
public class DarculaScrollBarUI extends ButtonlessScrollBarUI {

  @SuppressWarnings({"MethodOverridesStaticMethodOfSuperclass", "UnusedDeclaration"})
  public static ComponentUI createUI(JComponent c) {
    if (Registry.is("ide.scroll.new.layout")) {
      try {
        if (!SystemInfo.isMac) return (ComponentUI)newInstance(Class.forName("com.intellij.ui.components.DefaultScrollBarUI"));
        if (Registry.is("mac.scroll.new.ui")) return (ComponentUI)newInstance(Class.forName("com.intellij.ui.components.MacScrollBarUI"));
      }
      catch (Exception ignore) {
      }
    }
    return new DarculaScrollBarUI();
  }
}
