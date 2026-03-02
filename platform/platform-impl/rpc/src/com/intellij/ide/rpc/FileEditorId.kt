// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:ApiStatus.Experimental
package com.intellij.ide.rpc

import com.intellij.openapi.fileEditor.FileEditor
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.jetbrains.annotations.ApiStatus

fun FileEditorId.fileEditor(): FileEditor? {
  if (localKey != null) {
    return localKey
  }

  return null
}

fun FileEditor.rpcId(): FileEditorId {
  return FileEditorId(localKey = this)
}


@ApiStatus.Experimental
@Serializable
class FileEditorId internal constructor(
  @Transient @JvmField internal val localKey: FileEditor? = null
)
