// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.ui.panel;

import com.intellij.ui.InplaceButton;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * <code>ProgressPanel</code> is an object associated with each <code>JProgressBar</code> after
 * {@link PanelBuilder#createPanel()} has been called. You can use it to manipulate contents of
 * the progressbar panel such as label text, comment text and to receive the current action state.
 */
public abstract class ProgressPanel extends ComponentPanel {
  /**
   * Recursively finds instance of <code>JProgressBar</code> and takes <code>ProgressPanel</code>
   * associated with it.
   * @param parent is <code>JProgressBar</code> itself or any of its parents
   * @return instance of <code>ProgressPanel</code> or <code>null</code> if no <code>JProgressBar</code>
   * is found or it has no <code>ProgressPanel</code> associated.
   */
  public static @Nullable ProgressPanel getProgressPanel(@NotNull JComponent parent) {
    JProgressBar pb = UIUtil.findComponentOfType(parent, JProgressBar.class);
    return pb != null ? (ProgressPanel)pb.getClientProperty(DECORATED_PANEL_PROPERTY) : null;
  }

  /**
   * State of the progress bar panel.
   */
  public enum State {
    PLAYING,
    PAUSED,
    CANCELLED
  }

  /**
   * <p>Returns the state - the current action being executed. After creating a panel the state is always {@link State#PLAYING}</p>
   *
   * <p>If the panel was created with resume/pause actions the sate can go from {@link State#PLAYING}
   * to {@link State#PAUSED} and back when clicking resume/pause buttons.</p>
   *
   * <p>If the panel was created with cancel action then the state can go only from {@link State#PLAYING} to
   * {@link State#CANCELLED} after clicking the cancel button.</p>
   *
   * @return the state
   */
  public abstract @NotNull State getState();

  public abstract void setState(@NotNull State state);

  /**
   * @return the label text
   */
  public abstract String getLabelText();

  /**
   * Sets the label text.
   *
   * @param labelText new label text
   */
  public abstract void setLabelText(String labelText);

  public abstract void setLabelEnabled(boolean enabled);

  public abstract void setCommentEnabled(boolean enabled);

  public abstract void setText2(@Nullable String text);

  public abstract void setText2Enabled(boolean enabled);

  /**
   * <p>Enables/disables the top separator dynamically. This method has effect only when progressbar panel
   * was created with {@link ProgressPanelBuilder#withTopSeparator()}.</p>
   * <p>It makes sense to use this method when panels are added to/removed from the parent container dynamically
   * and it's needed to turn off the top separator for the first panel in the container.</p>
   *
   * @param enabled <code>true</code> to enable top separator which is default when crating progress panel with
   *                {@link ProgressPanelBuilder#withTopSeparator()}, <code>false</code> to disable.
   */
  public abstract void setSeparatorEnabled(boolean enabled);

  public abstract @Nullable JButton getCancelButtonAsButton();

  public abstract @Nullable InplaceButton getCancelButton();

  public abstract @Nullable InplaceButton getSuspendButton();
}
