/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.openapi.editor;

import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

public class CaretState {
  private final LogicalPosition caretPosition;
  private final int visualColumnAdjustment;
  private final LogicalPosition selectionStart;
  private final LogicalPosition selectionEnd;

  public CaretState(@Nullable LogicalPosition caretPosition, 
                    @Nullable LogicalPosition selectionStart, @Nullable LogicalPosition selectionEnd) {
    this(caretPosition, 0, selectionStart, selectionEnd);
  }

  /**
   * @param visualColumnAdjustment see {@link #getVisualColumnAdjustment()}
   */
  public CaretState(@Nullable LogicalPosition caretPosition, int visualColumnAdjustment, 
                    @Nullable LogicalPosition selectionStart, @Nullable LogicalPosition selectionEnd) {
    this.caretPosition = caretPosition;
    this.visualColumnAdjustment = visualColumnAdjustment;
    this.selectionStart = selectionStart;
    this.selectionEnd = selectionEnd;
  }

  @Nullable
  public LogicalPosition getCaretPosition(){
    return caretPosition;
  }

  /**
   * Sometimes logical caret position is not fully determining its visual position (e.g. around inlays). This value should be added to the
   * result of {@code editor.logicalToVisualPosition(caretState.getCaretPosition())}'s column, 
   * if one needs to calculate caret's visual position.
   */
  public int getVisualColumnAdjustment() {
    return visualColumnAdjustment;
  }

  @Nullable
  public LogicalPosition getSelectionStart() {
    return selectionStart;
  }

  @Nullable
  public LogicalPosition getSelectionEnd() {
    return selectionEnd;
  }

  @Override
  @NonNls
  public String toString() {
    return "CaretState{" +
           "caretPosition=" + caretPosition +
           (visualColumnAdjustment == 0 ? "" : (", visualColumnAdjustment=" + visualColumnAdjustment)) +
           ", selectionStart=" + selectionStart +
           ", selectionEnd=" + selectionEnd +
           '}';
  }
}
