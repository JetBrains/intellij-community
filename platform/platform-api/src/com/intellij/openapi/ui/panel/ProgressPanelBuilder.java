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

import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * Builder for standard progressbar panels.
 */
public interface ProgressPanelBuilder extends PanelBuilder {
  /**
   * Set label text.
   * @param text label text
   */
  ProgressPanelBuilder setLabelText(String text);

  /**
   * Set relative location of label. Label is relative to the progressbar.
   * @param location possible values: <code>SwingConstants.LEFT</code> or <code>SwingConstants.BOTTOM</code>
   */
  ProgressPanelBuilder setLabelLocation(@MagicConstant(intValues = {SwingConstants.LEFT, SwingConstants.TOP}) int location);

  /**
   * Enables cancel button (icon styled) and sets action for it. Can't coexist with play and pause actions.
   * @param cancelAction <code>Runnable</code> action.
   */
  ProgressPanelBuilder setCancelAction(@NotNull Runnable cancelAction);

  /**
   * @param asButton <code>true</code> if an ordinary button should be placed as "Cancel" button
   *                 instead of an icon, <code>false</code> otherwise. Default is <code>false</code>.
   */
  ProgressPanelBuilder setCancelAsButton(boolean asButton);

  /**
   * Enables play button (icon styled) and sets action for it. Can't coexist with cancel action.
   * @param playlAction <code>Runnable</code> action.
   */
  ProgressPanelBuilder setResumeAction(@NotNull Runnable playAction);

  /**
   * Enables pause button (icon styled) and sets action for it. Can't coexist with cancel action.
   * @param pauseAction <code>Runnable</code> action.
   */
  ProgressPanelBuilder setPauseAction(@NotNull Runnable pauseAction);

  /**
   * @param smallVariant <code>true</code> if small icons should be used for all kinds of actions.
   *                     Actual only for icon styled buttons. Default is <code>false</code>.
   */
  ProgressPanelBuilder setSmallVariant(boolean smallVariant);

  /**
   * @param enabled <code>true</code> if there is reserved place for a comment below the progressbar.
   *                Set it to <code>false</code> when creating compact progressbar panels.
   *                Default is <code>true</code>.
   */
  ProgressPanelBuilder setCommentEnabled(boolean enabled);

  /**
   * @param enabled Enables top separator for this panel. Use it when placing several progressbar
   *                panels in a grid depending on the design requirements.
   *                <code>true</code> enables separator above the progressbar panel.
   *                Default is <code>false</code>.
   */
  ProgressPanelBuilder setTopSeparatorEnabled(boolean enabled);
}
