// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.actionSystem.ex;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.util.Producer;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class DefaultCustomComponentAction extends AnAction implements CustomComponentAction {
  @NotNull private final Producer<? extends JComponent> myProducer;

  public DefaultCustomComponentAction(@NotNull Producer<? extends JComponent> producer) {
    myProducer = producer;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    //do nothing
  }

  @NotNull
  @Override
  public JComponent createCustomComponent(@NotNull Presentation presentation) {
    return myProducer.produce();
  }
}
