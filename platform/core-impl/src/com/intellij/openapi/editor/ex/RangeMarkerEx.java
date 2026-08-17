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

  /**
   * Some intervals could be marked with one or several "flavor" flags,
   * because some range markers can taste bitter, while others are sweet, I mean some range highlighters should be shown on the gutter area,
   * while some others - on the error stripe area only.
   * It's assumed the flags are remained constant after the marker is inserted into the tree (meaning this method will return the same value
   * when called several times during various points in the marker lifecycle), unless marker attributes are changed.
   * These flags are maintained during the tree transformations, and allows for faster iteration of these marked intervals,
   * see {@link com.intellij.openapi.editor.impl.IntervalTreeImpl#overlappingDeliciousIterator}.
   * For example, this feature can be used to store highlighters (among all others) that are shown at the error stripe, and iterate them quickly
   * during the editor redraw.
   * This method must return {@code 0} if the interval has no flavor, or one or several flags {@code OR}ed together,
   * if this interval has these flavors.
   * See {@link com.intellij.openapi.editor.impl.IntervalTreeImpl#nextAvailableFlavorFlag()} on how to create the flag in the first place.
   */
  @ApiStatus.Internal
  default byte getFlavorFlags() {
    return 0;
  }

  @ApiStatus.Internal
  default long getScalarRange() {
    return TextRangeScalarUtil.toScalarRange(getTextRange());
  }
}
