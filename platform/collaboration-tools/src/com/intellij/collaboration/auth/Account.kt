// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.collaboration.auth

import java.util.*

abstract class Account {
  abstract val id: String

  final override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is Account) return false

    if (id != other.id) return false

    return true
  }

  final override fun hashCode(): Int {
    return id.hashCode()
  }

  companion object {
    fun generateId() = UUID.randomUUID().toString()
  }
}