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

public interface ComponentPanelBuilder extends PanelBuilder {

  /**
   * @param labelText text for the label.
   *
   * @return <code>this</code>
   */
  ComponentPanelBuilder withLabel(@NotNull String labelText);

  /**
   * Move label on top of the owner component. Default position is on the left of the owner component.
   *
   * @return <code>this</code>
   */
  ComponentPanelBuilder moveLabelOnTop();

  /**
   * @param commentText help context styled text written below the owner component.
   *
   * @return <code>this</code>
   */
  ComponentPanelBuilder withComment(@NotNull String commentText);

  /**
   * Move comment to the right of the owner component. Default position is below the owner component.
   *
   * @return <code>this</code>
   */
  ComponentPanelBuilder moveCommentRight();

  /**
   * Enables the help tooltip icon on the right of the owner component and sets the description text for the tooltip.
   *
   * @param description help tooltip description.
   *
   * @return <code>this</code>
   */
  ComponentPanelBuilder withTooltip(@NotNull String description);

  /**
   * Sets optional help tooltip link and link action.
   *
   * @param linkText help tooltip link text.
   *
   * @param action help tooltip link action.
   *
   * @return <code>this</code>
   */
  ComponentPanelBuilder withTooltipLink(@NotNull String linkText, @NotNull Runnable action);
}
