// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package andel.text

import andel.operation.Operation

/**
 * Mutable version of [TextView]
 * */
interface MutableTextView : TextView {
  /**
   * Mutates the copy of the original text.
   * All subsequent queries to this view will reflect the operation.
   * [TextView.text] will build a new version of the original [Text], the old version is left intact.  
   * */
  fun edit(operation: Operation)
}