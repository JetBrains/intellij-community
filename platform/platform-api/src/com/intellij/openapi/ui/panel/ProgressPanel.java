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

import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * <code>ProgressPanel</code> is an object associated with each <code>JProgressBar</code> after
 * <code>PanelBuilder.createPanel</code> has been called. You can use it to manipulate contents of
 * the progressbar panel such as label text, comment text and to receive the current action state.
 */
public abstract class ProgressPanel {
  public static final String LABELED_PANEL_PROPERTY = "JComponent.labeledPanel";

  /**
   * <code>LABELED_PANEL_PROPERTY</code> is installed on the owner component after <code>ProgressPanelBuilder.createPanel</code>
   * has been called.
   * @param parent is <code>JProgressBar</code> itself or any of its parents
   * @return instance of <code>ProgressPanel</code> or <code>null</code>
   */
  @Nullable
  public static ProgressPanel forComponent(JComponent parent) {
    JProgressBar pb = UIUtil.findComponentOfType(parent, JProgressBar.class);
    return pb != null ? (ProgressPanel)pb.getClientProperty(LABELED_PANEL_PROPERTY) : null;
  }

  public enum State {
    PLAYING,
    PAUSED,
    CANCELLED
  }

  /**
   * @return the state - the current action being executed. After creating a panel the state is always <code>State.PLAYING</code>
   * If the panel was created with resume/pause actions the sate can go from <code>State.PLAYING</code>
   * to <code>State.PAUSED</code> and back when clicking resume/pause buttons.
   * If the panel was created with cancel action then the state can go from <code>State.PLAYING</code> to
   * <code>State.CANCELLED</code> only after clicking the cancel button.
   */
  public abstract State getState();

  /**
   * @return the label text
   */
  public abstract String getLabelText();

  /**
   * Set the label text.
   */
  public abstract void setLabelText(String labelText);

  /**
   * @return the comment text.
   */
  public abstract String getCommentText();

  /**
   * Set the comment text.
   */
  public abstract void setCommentText(String comment);

  public abstract void setSeparatorEnabled(boolean enabled);
}
