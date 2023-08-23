// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.impl.nestedCompletions

import com.intellij.openapi.editor.Document

internal class DocumentChange<T : Comparable<T>>(val key: T, val sideEffect: Document.(T) -> Unit)

internal fun documentChangeAt(offset: Int, task: Document.(Int) -> Unit) = DocumentChange(offset, task)

/**
 * Use this utility if you're like me, and you too have a mushy brain that's struggling to comprehend the order of document changes.
 * It will run tasks from right to left, which makes thinking about the mutations easier.
 */
internal fun <T : Comparable<T>> Document.applyChangesOrdered(vararg tasks: DocumentChange<T>) {
  tasks.sortByDescending { it.key }
  for (task in tasks) task.sideEffect(this, task.key)
}