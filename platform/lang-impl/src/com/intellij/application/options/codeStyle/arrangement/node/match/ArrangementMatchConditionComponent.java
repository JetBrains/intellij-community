/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.application.options.codeStyle.arrangement.node.match;

import com.intellij.psi.codeStyle.arrangement.model.ArrangementMatchCondition;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;

/**
 * Component which manages {@link ArrangementMatchCondition match condition} UI representation.
 * 
 * @author Denis Zhdanov
 * @since 8/10/12 11:22 AM
 */
public interface ArrangementMatchConditionComponent {
  
  @NotNull
  ArrangementMatchCondition getMatchCondition();

  /**
   * @return    UI component for the {@link #getMatchCondition() target match condition}
   */
  @NotNull
  JComponent getUiComponent();

  /**
   * @return    screen bounds for the {@link #getUiComponent() target UI component} (if known)
   */
  @Nullable
  Rectangle getScreenBounds();

  /**
   * Instructs current component that it should {@link #getUiComponent() draw} itself according to the given 'selected' state.
   *
   * @param selected  flag that indicates if current component should be drawn as 'selected'
   */
  void setSelected(boolean selected);

  /**
   * Notifies current component about mose move event.
   * <p/>
   * Primary intention is to allow to react on event like 'on mouse hover' etc. We can't do that by subscribing to the
   * mouse events at the {@link #getUiComponent() corresponding UI control} because it's used only as a renderer and is not put
   * to the containers hierarchy, hence, doesn't receive mouse events.
   * 
   * @param event  target mouse move event
   * @return       bounds to be repainted (in screen coordinates) if any; <code>null</code> otherwise
   */
  @Nullable
  Rectangle onMouseMove(@NotNull MouseEvent event);

  void onMousePress(@NotNull MouseEvent event);

  @Nullable
  Rectangle onMouseExited();

  @Nullable
  Rectangle onMouseEntered(@NotNull MouseEvent e);
}
