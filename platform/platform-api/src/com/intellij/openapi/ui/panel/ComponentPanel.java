// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.ui.panel;

import com.intellij.openapi.util.NlsContexts;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * <code>ComponentPanel</code> is an object associated with each <code>JComponent</code> after
 * {@link PanelBuilder#createPanel()} has been called. You can use it to manipulate contents of
 * the component panel. Currently comment text getter/setter is available.
 */
public abstract class ComponentPanel {
  /**
   * Property if this name installed on the owner component after {@link PanelBuilder#createPanel()}
   * has been called.
   */
  public static final String DECORATED_PANEL_PROPERTY = "JComponent.decoratedPanel";

  /**
   * Takes ComponentPanel instance associated with the given <code>JComponent</code>
   * @param component is the owner
   * @return instance of <code>ComponentPanel</code> or <code>null</code> there is no
   * <code>ComponentPanel</code> associated with the owner.
   */
  @Nullable
  public static ComponentPanel getComponentPanel(@NotNull JComponent component) {
    return (ComponentPanel)component.getClientProperty(DECORATED_PANEL_PROPERTY);
  }

  /**
   * @return the comment text.
   */
  public abstract @NlsContexts.DetailedDescription String getCommentText();

  /**
   * Set the comment text.
   *
   * @param commentText new comment text
   */
  public abstract void setCommentText(@NlsContexts.DetailedDescription String commentText);
}
