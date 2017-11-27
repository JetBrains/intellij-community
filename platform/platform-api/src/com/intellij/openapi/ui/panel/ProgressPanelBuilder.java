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

import org.jetbrains.annotations.NotNull;

/**
 * Builder for standard progressbar panels.
 */
public interface ProgressPanelBuilder extends PanelBuilder {
  /**
   * Set label text.
   * @param text label text
   */
  ProgressPanelBuilder withLabel(String text);

  /**
   * Move comment to the left of the progress bar.
   * Default position is above the progress bar.
   */
  ProgressPanelBuilder moveLabelLeft();

  /**
   * Enables cancel button and sets action for it. Can't coexist with play and pause actions.
   * @param cancelAction <code>Runnable</code> action.
   */
  ProgressPanelBuilder withCancel(@NotNull Runnable cancelAction);

  /**
   * Cancel button will look like an ordinary button rather than as icon.
   * Default is icon styled cancel button.
   */
  ProgressPanelBuilder andCancelAsButton();

  /**
   * Enables play button (icon styled) and sets action for it. Can't coexist with cancel action.
   * @param playAction <code>Runnable</code> action.
   */
  ProgressPanelBuilder withResume(@NotNull Runnable playAction);

  /**
   * Enables pause button (icon styled) and sets action for it. Can't coexist with cancel action.
   * @param pauseAction <code>Runnable</code> action.
   */
  ProgressPanelBuilder withPause(@NotNull Runnable pauseAction);

  /**
   * Switch to small icons version.
   */
  ProgressPanelBuilder andSmallIcons();

  /**
   * Switch off the comment and don't reserve place for in th the layout.
   * This makes overall panel size smaller.
   */
  ProgressPanelBuilder withoutComment();

  /**
   * Enable separator on top of the panel.
   */
  ProgressPanelBuilder withTopSeparator();
}
