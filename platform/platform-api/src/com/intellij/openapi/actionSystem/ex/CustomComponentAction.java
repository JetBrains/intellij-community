// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.actionSystem.ex;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public interface CustomComponentAction {

  Key<JComponent> COMPONENT_KEY = Key.create("customComponent");
  Key<AnAction> ACTION_KEY = Key.create("customComponentAction");

  /**
   * @return custom JComponent that represents action in UI.
   * You (as a client/implementor) or this interface are not allowed to invoke
   * this method directly. Only action system can invoke it!
   * <br/>
   * <br/>
   * The component should not be stored in the action instance because it may
   * be shown on several toolbars simultaneously. Use {@link CustomComponentAction#COMPONENT_KEY}
   * to retrieve current component from a Presentation instance in {@link AnAction#update(AnActionEvent)} method.
   */
  @NotNull
  JComponent createCustomComponent(@NotNull Presentation presentation);
}
