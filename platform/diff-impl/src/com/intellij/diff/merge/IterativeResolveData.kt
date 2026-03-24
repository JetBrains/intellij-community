// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.merge

import com.intellij.openapi.util.Key
import com.intellij.openapi.util.UserDataHolder
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object IterativeResolveSupport {
  private val ITERATIVE_RESOLVE_DATA: Key<MergeConflictModel> = Key.create("iterative.resolve.data")

  @JvmStatic
  fun setData(holder: UserDataHolder, model: MergeConflictModel): Unit =
    holder.putUserData(ITERATIVE_RESOLVE_DATA, model)

  @JvmStatic
  fun getData(holder: UserDataHolder?): MergeConflictModel? = holder?.getUserData(ITERATIVE_RESOLVE_DATA)

  @JvmStatic
  fun hasIterativeData(holder: UserDataHolder?): Boolean = getData(holder) != null
}