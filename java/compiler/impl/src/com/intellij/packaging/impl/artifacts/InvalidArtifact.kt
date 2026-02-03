// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.packaging.impl.artifacts

import com.intellij.packaging.artifacts.Artifact
import org.jetbrains.annotations.Nls

interface InvalidArtifact: Artifact {
  @Nls(capitalization = Nls.Capitalization.Sentence)
  fun getErrorMessage(): String
}
