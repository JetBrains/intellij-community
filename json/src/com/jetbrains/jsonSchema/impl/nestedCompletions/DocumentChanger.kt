// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.impl.nestedCompletions

import com.intellij.openapi.editor.Document

internal class DocumentChange<T : Comparable<T>>(val key: T, val sideEffect: Document.(T) -> Unit)

internal fun documentChangeAt(offset: Int, task: Document.(Int) -> Unit) = DocumentChange(offset, task)

/** This utility will run tasks from right to left, which makes comprehending mutations easier. */
internal fun <T : Comparable<T>> Document.applyChangesOrdered(vararg tasks: DocumentChange<T>) {
  tasks.sortByDescending { it.key }
  for (task in tasks) task.sideEffect(this, task.key)
}