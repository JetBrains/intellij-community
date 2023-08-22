// Copyright 2000-2017 JetBrains s.r.o.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
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
  @Nullable
  public static ProgressPanel getProgressPanel(@NotNull JComponent parent) {
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
  @NotNull
  public abstract State getState();

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

  @Nullable
  public abstract JButton getCancelButtonAsButton();

  @Nullable
  public abstract InplaceButton getCancelButton();

  @Nullable
  public abstract InplaceButton getSuspendButton();
}
