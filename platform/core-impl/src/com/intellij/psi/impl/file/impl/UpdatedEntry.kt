// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.file.impl

internal class UpdatedEntry(
  val newEntry: FileViewProviderCache.Entry,
  val oldEntry: FileViewProviderCache.Entry,
)
