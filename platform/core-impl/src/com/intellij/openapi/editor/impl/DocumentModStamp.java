// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl;

import com.intellij.util.LocalTimeCounter;

/**
 * This class is responsible for creating fresh document modification stamps during document text changes.
 */
final class DocumentModStamp {
  static long next() {
    return LocalTimeCounter.currentTime();
  }

  private DocumentModStamp() {
  }
}
