/*
 * Copyright 2000-2019 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.psi.codeStyle.arrangement.std;

import com.intellij.application.options.codeStyle.arrangement.color.ArrangementColorsProvider;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.codeStyle.arrangement.model.ArrangementMatchCondition;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.Set;

/**
 * Defines a contract for UI component used at standard arrangement settings managing code.
 * <p/>
 * It's assumed that there is a dedicated implementation of this interface for every {@link StdArrangementTokenUiRole}.
 */
public interface ArrangementUiComponent {

  @Nullable
  ArrangementSettingsToken getToken();

  @NotNull
  @Unmodifiable
  Set<ArrangementSettingsToken> getAvailableTokens();

  void chooseToken(@NotNull ArrangementSettingsToken data) throws IllegalArgumentException, UnsupportedOperationException;

  @NotNull
  ArrangementMatchCondition getMatchCondition();

  @NotNull
  JComponent getUiComponent();

  /**
   * We use 'enabled by user' property name here in order to avoid clash
   * with {@link Component#isEnabled() standard AWT 'enabled' property}.
   *
   * @return {@code true} if current ui token is enabled; {@code false} otherwise
   */
  boolean isEnabled();

  void setEnabled(boolean enabled);

  /**
   * @return screen bounds for the {@link #getUiComponent() target UI component} (if known)
   */
  @Nullable
  Rectangle getScreenBounds();

  boolean isSelected();

  /**
   * Instructs current component that it should {@link #getUiComponent() draw} itself according to the given 'selected' state.
   *
   * @param selected flag that indicates if current component should be drawn as 'selected'
   */
  void setSelected(boolean selected);

  void setData(@NotNull Object data);

  void reset();

  /**
   * Notifies current component about mose move event.
   * <p/>
   * Primary intention is to allow to react on event like 'on mouse hover' etc. We can't do that by subscribing to the
   * mouse events at the {@link #getUiComponent() corresponding UI control} because it's used only as a renderer and is not put
   * to the containers hierarchy, hence, doesn't receive mouse events.
   *
   * @param event target mouse move event
   * @return bounds to be repainted (in screen coordinates) if any; {@code null} otherwise
   */
  @Nullable
  Rectangle onMouseMove(@NotNull MouseEvent event);

  void onMouseRelease(@NotNull MouseEvent event);

  @Nullable
  Rectangle onMouseExited();

  @Nullable
  Rectangle onMouseEntered(@NotNull MouseEvent e);

  /**
   * @param width  the width to get baseline for
   * @param height the height to get baseline for
   * @return baseline's y coordinate if applicable; negative value otherwise
   */
  int getBaselineToUse(int width, int height);

  void setListener(@NotNull Listener listener);

  /**
   * Method to process second click on the component,
   * e.g. we can deselect the component or invert it condition.
   */
  void handleMouseClickOnSelected();

  /**
   * For a condition that can't be disabled,
   * e.g. 'not public' can be used with any other rule like 'private' or 'not private'.
   */
  boolean alwaysCanBeActive();

  interface Factory {
    ExtensionPointName<Factory> EP_NAME = ExtensionPointName.create("com.intellij.rearranger.ui");

    @Nullable
    ArrangementUiComponent build(@NotNull StdArrangementTokenUiRole role,
                                 @NotNull List<? extends ArrangementSettingsToken> tokens,
                                 @NotNull ArrangementColorsProvider colorsProvider,
                                 @NotNull ArrangementStandardSettingsManager settingsManager);
  }

  interface Listener {
    void stateChanged();
  }
}
