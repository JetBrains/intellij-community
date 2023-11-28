// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.impl.correctness

import com.intellij.platform.ml.impl.correctness.autoimport.ImportFixer
import com.intellij.platform.ml.impl.correctness.checker.CorrectnessCheckerBase
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
abstract class MLCompletionCorrectnessSupporterBase : MLCompletionCorrectnessSupporter {
  override val correctnessChecker = CorrectnessCheckerBase()
  override val importFixer: ImportFixer = ImportFixer.EMPTY
}