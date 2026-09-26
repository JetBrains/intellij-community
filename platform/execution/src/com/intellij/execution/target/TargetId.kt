// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.target

/**
 * Unique id of particular target.
 * This class used by cache, so no references to any external entities are allowed.
 * [String], primitive or any kind of UID is ok.`
 */
@JvmInline
value class TargetId(val value: Any) {
  override fun toString(): String = value.toString()
}
