// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.transformer

import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface TextPresentationTransformer {
  fun fromPersistent(text: CharSequence, virtualFile: VirtualFile):CharSequence

  fun toPersistent(text: CharSequence, virtualFile: VirtualFile):CharSequence
}