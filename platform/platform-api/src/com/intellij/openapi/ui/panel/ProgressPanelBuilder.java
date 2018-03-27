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
   *
   * @param text label text
   * @return <code>this</code>
   */
  ProgressPanelBuilder withLabel(@NotNull String text);

  /**
   * Move comment to the left of the progress bar. Default position is above the progress bar.
   *
   * @return <code>this</code>
   */
  ProgressPanelBuilder moveLabelLeft();

  /**
   * Enables cancel button and sets action for it. Can't coexist with play and pause actions.
   *
   * @param cancelAction <code>Runnable</code> action.
   * @return <code>this</code>
   */
  ProgressPanelBuilder withCancel(@NotNull Runnable cancelAction);

  /**
   * Cancel button will look like an ordinary button rather than as icon. Default is icon styled cancel button.
   *
   * @return <code>this</code>
   */
  ProgressPanelBuilder andCancelAsButton();

  /**
   * Enables play button (icon styled) and sets action for it. Can't coexist with cancel action.
   *
   * @param playAction <code>Runnable</code> action.
   * @return <code>this</code>
   */
  ProgressPanelBuilder withResume(@NotNull Runnable playAction);

  /**
   * Enables pause button (icon styled) and sets action for it. Can't coexist with cancel action.
   *
   * @param pauseAction <code>Runnable</code> action.
   * @return <code>this</code>
   */
  ProgressPanelBuilder withPause(@NotNull Runnable pauseAction);

  /**
   * Switch to small icons version.
   *
   * @return <code>this</code>
   */
  ProgressPanelBuilder andSmallIcons();

  /**
   * Switch off the comment and don't reserve place for it in the layout. This makes overall panel height smaller.
   *
   * @return <code>this</code>
   */
  ProgressPanelBuilder withoutComment();

  /**
   * Enable separator on top of the panel.
   *
   * @return <code>this</code>
   */
  ProgressPanelBuilder withTopSeparator();
}
