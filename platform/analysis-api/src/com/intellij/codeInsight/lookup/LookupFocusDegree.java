// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.lookup;

/**
 * Specifies how lookup behaves on `enter`, `tab`, and other context-dependent keys.
 */
public enum LookupFocusDegree {
  /**
   * The top lookup item matching the prefix is preselected and is inserted on `enter`,
   * `tab`, or other context-dependent keys like space or dot.
   */
  FOCUSED,

  /**
   * Similar to {@link LookupFocusDegree#FOCUSED} but the top matching lookup item is not inserted on
   * context-dependent keys. Becomes {@link LookupFocusDegree#FOCUSED} on `up` or `down`.
   */
  SEMI_FOCUSED,

  /**
   * The top lookup item matching the prefix is not preselected. `Tab` inserts the top item,
   * `enter` works as usual in the editor (inserts newline, possibly with indents).
   */
  UNFOCUSED
}
