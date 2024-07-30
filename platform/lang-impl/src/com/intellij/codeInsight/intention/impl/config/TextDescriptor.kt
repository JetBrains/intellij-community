// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.intention.impl.config

import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import java.io.IOException

interface TextDescriptor {
  @Throws(IOException::class)
  fun getText(): @Nls String

  fun getFileName(): @NonNls String
}
