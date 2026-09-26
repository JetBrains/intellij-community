// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.merge

/**
 * Indication for special types of conflicts. Content of a 'missing' side can be ignored, even if provided.
 */
enum class ConflictType {
  DEFAULT,
  ADDED_ADDED,
  DELETED_MODIFIED,
  MODIFIED_DELETED
}