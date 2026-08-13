/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

package com.intellij.openapi.editor.ex;

import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.util.TextRangeScalarUtil;
import org.jetbrains.annotations.ApiStatus;

public interface RangeMarkerEx extends RangeMarker {
  /**
   * @return identifier unique across all live range markers.
   * Must not be called for the disposed range marker.
   */
  long getId();

  @ApiStatus.Internal
  default void setStickingToRight(boolean value) {}

  @ApiStatus.Internal
  default long getScalarRange() {
    return TextRangeScalarUtil.toScalarRange(getTextRange());
  }
}
