// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.slicer;

import com.intellij.openapi.util.NlsContexts;

public final class SliceFilterParseException extends Exception {
  public SliceFilterParseException(@NlsContexts.DialogMessage String message) {
    super(message);
  }
}
