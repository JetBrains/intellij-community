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
package com.intellij.application.options.codeStyle.arrangement.match;

import com.intellij.psi.codeStyle.arrangement.model.ArrangementMatchCondition;
import com.intellij.psi.codeStyle.arrangement.model.ArrangementNameMatchCondition;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;

/**
 * @author Denis Zhdanov
 * @since 11/14/12 12:24 PM
 */
public class ArrangementNameConditionComponent implements ArrangementMatchConditionComponent {

  @NotNull private final ArrangementNameMatchCondition myCondition;

  public ArrangementNameConditionComponent(@NotNull ArrangementNameMatchCondition condition) {
    myCondition = condition;
  }

  @NotNull
  @Override
  public ArrangementMatchCondition getMatchCondition() {
    return myCondition;
  }

  @NotNull
  @Override
  public JComponent getUiComponent() {
    // TODO den implement 
    return null;
  }

  @Nullable
  @Override
  public Rectangle getScreenBounds() {
    // TODO den implement 
    return null;
  }

  @Override
  public void setSelected(boolean selected) {
    // TODO den implement 
  }

  @Nullable
  @Override
  public Rectangle onMouseMove(@NotNull MouseEvent event) {
    // TODO den implement 
    return null;
  }

  @Override
  public void onMouseRelease(@NotNull MouseEvent event) {
    // TODO den implement 
  }

  @Nullable
  @Override
  public Rectangle onMouseExited() {
    // TODO den implement 
    return null;
  }

  @Nullable
  @Override
  public Rectangle onMouseEntered(@NotNull MouseEvent e) {
    // TODO den implement 
    return null;
  }
}
