// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor

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

    fun createFileEditorComposite(allEditors: List<FileEditor>,
                                  allProviders: List<FileEditorProvider>,
                                  isPreview: Boolean = false): FileEditorComposite {
      return object : FileEditorComposite {
        override val allEditors: List<FileEditor>
          get() = allEditors
        override val allProviders: List<FileEditorProvider>
          get() = allProviders
        override val isPreview: Boolean
          get() = isPreview
      }
    }

    fun fromPair(pair: Pair<Array<FileEditor>, Array<FileEditorProvider>>): FileEditorComposite {
      return createFileEditorComposite(pair.first.asList(), pair.second.asList())
    }
  }

  val allEditors: List<FileEditor>
  val allProviders: List<FileEditorProvider>
  val isPreview: Boolean

  @Internal
  fun retrofit(): com.intellij.openapi.util.Pair<Array<FileEditor>, Array<FileEditorProvider>> {
    return com.intellij.openapi.util.Pair(allEditors.toTypedArray(), allProviders.toTypedArray())
  }
}
