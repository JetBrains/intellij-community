// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing

import com.intellij.psi.impl.cache.impl.todo.TodoIndexEntry
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object TodoIndexId {
  @Deprecated("")
  val name: ID<Int, MutableMap<TodoIndexEntry, Int>> by lazy { ID.create<Int, MutableMap<TodoIndexEntry, Int>>("TodoIndex") }
}
