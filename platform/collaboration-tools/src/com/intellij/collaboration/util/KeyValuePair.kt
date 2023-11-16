// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.util

import com.intellij.openapi.util.Key
import com.intellij.openapi.util.UserDataHolder
import org.jetbrains.annotations.ApiStatus.Experimental

@Experimental
data class KeyValuePair<T>(val key: Key<T>, val value: T?)

@Experimental
fun <T> UserDataHolder.putData(keyValue: KeyValuePair<T>) {
  putUserData(keyValue.key, keyValue.value)
}

@Experimental
fun UserDataHolder.clearData(keyValue: KeyValuePair<*>) {
  putUserData(keyValue.key, null)
}