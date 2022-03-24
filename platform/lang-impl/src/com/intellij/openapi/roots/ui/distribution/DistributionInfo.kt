// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.roots.ui.distribution

import org.jetbrains.annotations.Nls

interface DistributionInfo {
  val name: @Nls(capitalization = Nls.Capitalization.Sentence) String
  val description: @Nls(capitalization = Nls.Capitalization.Sentence) String?
}