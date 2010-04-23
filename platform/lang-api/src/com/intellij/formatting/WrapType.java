/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.formatting;

import com.intellij.psi.codeStyle.CodeStyleSettings;

import java.util.HashMap;
import java.util.Map;

/**
 * Defines possible types of a wrap.
 *
 * @see Wrap#createWrap(WrapType, boolean)
 * @see Wrap#createChildWrap(Wrap, WrapType, boolean)
 */

public enum WrapType {

  /**
   * A line break is always inserted before the start of the element.
   * This corresponds to the "Wrap always" setting in Global Code Style | Wrapping.
   */
  ALWAYS(CodeStyleSettings.WRAP_ALWAYS),

  /**
   * A line break is inserted before the start of the element if the right edge
   * of the element goes beyond the specified wrap margin.
   * This corresponds to the "Wrap if long" setting in Global Code Style | Wrapping.
   */
  NORMAL(CodeStyleSettings.WRAP_AS_NEEDED),

  /**
   * A line break is never inserted before the start of the element.
   * This corresponds to the "Do not wrap" setting in Global Code Style | Wrapping.
   */
  NONE(CodeStyleSettings.DO_NOT_WRAP),

  /**
   * A line break is inserted before the start of the element if it is a part
   * of list of elements of the same type and at least one of the elements was wrapped.
   * This corresponds to the "Chop down if long" setting in Global Code Style | Wrapping.
   */
  CHOP_DOWN_IF_LONG(CodeStyleSettings.WRAP_ON_EVERY_ITEM);

  private static final Map<Integer, WrapType> LEGACY_MAPPINGS = new HashMap<Integer, WrapType>();

  static {
    for (WrapType wrapType : values()) {
      LEGACY_MAPPINGS.put(wrapType.getLegacyRepresentation(), wrapType);
    }
  }

  private final int myLegacyRepresentation;

  WrapType(int legacyRepresentation) {
    myLegacyRepresentation = legacyRepresentation;
  }

  /**
   * Allows to retrieve wrap type by it's legacy non-type-safe representation (see {@link #getLegacyRepresentation()}).
   *
   * @param value   legacy representation of the target wrap type
   * @return        wrap type which {@link #getLegacyRepresentation() legacyRepresentation} is equal to the given value if any;
   *                {@link #CHOP_DOWN_IF_LONG} otherwise
   */
  public static WrapType byLegacyRepresentation(int value) {
    WrapType result = LEGACY_MAPPINGS.get(value);
    return result == null ? CHOP_DOWN_IF_LONG : result;
  }

  /**
   * Wrapping types were used as a primitive constants during this enum introduction (e.g. {@link CodeStyleSettings#DO_NOT_WRAP} etc).
   * <p/>
   * It's possible to map that legacy representation to the object-level enum member via {@link #byLegacyRepresentation(int)}.
   * <p/>
   * Current getter exposes legacy value associated with the current object-level wrap type representation.
   *
   * @return      legacy representation of the current wrap type
   */
  public int getLegacyRepresentation() {
    return myLegacyRepresentation;
  }
}
