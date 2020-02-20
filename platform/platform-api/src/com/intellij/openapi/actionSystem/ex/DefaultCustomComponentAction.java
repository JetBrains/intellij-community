// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.actionSystem.ex;

import com.intellij.ide.lightEdit.LightEditCompatible;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.function.Supplier;

public class DefaultCustomComponentAction extends AnAction implements CustomComponentAction, LightEditCompatible {
  @NotNull private final Supplier<? extends JComponent> myProducer;

  public DefaultCustomComponentAction(@NotNull Supplier<? extends JComponent> producer) {
    myProducer = producer;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    //do nothing
  }

  @NotNull
  @Override
  public JComponent createCustomComponent(@NotNull Presentation presentation, @NotNull String place) {
    return myProducer.get();
  }
}
