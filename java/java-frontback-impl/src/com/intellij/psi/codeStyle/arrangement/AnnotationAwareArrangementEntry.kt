// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.codeStyle.arrangement

/**
 * Interface for arrangement entries that are aware if they have annotations or not.
 * @see JavaElementArrangementEntry
 */
internal interface AnnotationAwareArrangementEntry : ArrangementEntry {
  fun hasAnnotation(): Boolean

  fun setHasAnnotation()
}