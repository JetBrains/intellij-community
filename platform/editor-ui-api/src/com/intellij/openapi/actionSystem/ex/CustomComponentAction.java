// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.actionSystem.ex;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

public interface CustomComponentAction {
  Key<JComponent> COMPONENT_KEY = Key.create("customComponent");
  Key<AnAction> ACTION_KEY = Key.create("customComponentAction");

  /**
   * Creates a new component that represents action in various toolbars.
   * <br/>
   * <ul>
   *   <li>The method shall always return a new component instance.
   *   Do not store or cache the component in the action instance or anywhere else.
   *   That is because several toolbars can show the same action simultaneously.</li>
   *   <li>Configure listeners, and register {@link com.intellij.openapi.Disposable} if needed in the component {@link Component#addNotify()}.
   *   That is because a component can be created and then thrown away without really going to the screen.
   *   </li>
   *   <li>Do not access and refresh the component in {@link AnAction#update(AnActionEvent)} method using {@link CustomComponentAction#COMPONENT_KEY}.
   *   That is especially true for actions capable of updating their presentations on a background thread.
   *   Instead, {@link CustomComponentAction#updateCustomComponent(JComponent, Presentation)}
   *   or a {@link java.beans.PropertyChangeListener} shall be used to synchronize the provided {@link Presentation} and the component state.
   *   That is because an update can be called on any presentation and the result can be thrown away without really applying.
   *   Also, for {@link com.intellij.openapi.actionSystem.ActionUpdateThread#BGT} actions the update is called on a background thread.</li>
   *   <li>ActionToolbar will apply its customization for the component. In particular it will set the {@link ActionButtonLook}
   *   if the component is derived from {@link com.intellij.openapi.actionSystem.impl.ActionButton}.
   *   </li>
   * </ul>
   *
   * @see com.intellij.openapi.actionSystem.impl.ActionButton
   * @see com.intellij.openapi.actionSystem.ActionUpdateThread
   */
  default @NotNull JComponent createCustomComponent(@NotNull Presentation presentation, @NotNull String place) {
    return createCustomComponent(presentation);
  }

  /**
   * This method shall be used to update the specific custom component after a successful action update.
   */
  default void updateCustomComponent(@NotNull JComponent component, @NotNull Presentation presentation) {
  }

  /** @deprecated Use {@link CustomComponentAction#createCustomComponent(Presentation, String)} */
  @Deprecated
  default @NotNull JComponent createCustomComponent(@NotNull Presentation presentation) {
    throw new AssertionError();
  }
}
