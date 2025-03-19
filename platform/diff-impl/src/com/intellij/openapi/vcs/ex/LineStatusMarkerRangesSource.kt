// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.ex

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
interface LineStatusMarkerRangesSource<out R : Range> {
  /**
   * Check if [getRanges] will return valid ranges
   */
  fun isValid(): Boolean

  /**
   * Changed line ranges between two states of a document
   */
  fun getRanges(): List<R>?

  /**
   * Try and find a range at the same lines as [range]
   */
  fun findRange(range: Range): R?
}