// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.blockingCallsDetection

sealed class ContextType(
  val isDefinitelyKnown: Boolean,
  val priority: Int
) {
  /*
     * Blocking calls inside are NOT ALLOWED
     */
  object NONBLOCKING : ContextType(true, 1)

  /*
   * Blocking calls inside are ALLOWED
   */
  object BLOCKING : ContextType(true, 0)

  /*
   * No information about whether it is ok to call blocking methods inside current context or not
   */
  object UNSURE : ContextType(false, -1)
}