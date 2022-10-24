// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor

import com.intellij.openapi.util.Pair
import org.jetbrains.annotations.ApiStatus.Internal

interface FileEditorComposite {
  companion object {
    @Internal
    val EMPTY: FileEditorComposite = object : FileEditorComposite {
      override val allEditors: List<FileEditor>
        get() = emptyList()
      override val allProviders: List<FileEditorProvider>
        get() = emptyList()
      override val isPreview: Boolean
        get() = false
    }

    fun fromPair(pair: Pair<Array<FileEditor>, Array<FileEditorProvider>>): FileEditorComposite {
      return object : FileEditorComposite {
        override val allEditors: List<FileEditor>
          get() = pair.getFirst().asList()
        override val allProviders: List<FileEditorProvider>
          get() = pair.getSecond().asList()
        override val isPreview: Boolean
          get() = false
      }
    }
  }

  val allEditors: List<FileEditor>
  val allProviders: List<FileEditorProvider>
  val isPreview: Boolean

  @Internal
  fun retrofit(): Pair<Array<FileEditor>, Array<FileEditorProvider>> = Pair(allEditors.toTypedArray(), allProviders.toTypedArray())
}
