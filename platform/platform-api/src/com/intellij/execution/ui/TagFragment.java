// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.ui;

import com.intellij.ui.JBColor;

import javax.swing.*;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

public class TagFragment<Settings> extends SettingsEditorFragment<Settings, JLabel> {
  public TagFragment(String name, Predicate<Settings> getter, BiConsumer<Settings, Boolean> setter) {
    super(name, new JLabel(name),
          (settings, label) -> label.setVisible(getter.test(settings)),
          (settings, label) -> setter.accept(settings, label.isVisible()));
    getComponent().setOpaque(true);
    getComponent().setBackground(JBColor.LIGHT_GRAY);
  }

  @Override
  public boolean isTag() {
    return true;
  }
}
