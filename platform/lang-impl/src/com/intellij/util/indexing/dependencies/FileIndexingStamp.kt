// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.dependencies

interface FileIndexingStamp {
  /**
   * Number representing IndexingStamp. Do not compare this number to any other stamps except for equality.
   */
  fun toInt(): Int
}