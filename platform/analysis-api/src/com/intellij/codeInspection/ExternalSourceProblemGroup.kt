// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection

import com.intellij.lang.annotation.ProblemGroup
import org.jetbrains.annotations.ApiStatus


/**
 * This interface needs to be implemented by implementers of {@link ProblemGroup}
 * that created from external annotations.
 */
@ApiStatus.Internal
interface ExternalSourceProblemGroup : ProblemGroup {

  /**
   * Returns the name of inspection or check, performed by external annotator.
   */
  fun getExternalCheckName(): String?
}