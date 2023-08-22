// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.templates.github;

import com.intellij.openapi.util.NlsContexts.DialogMessage;

public final class GeneratorException extends Exception {
  public GeneratorException(@DialogMessage String message) {
    super(message);
  }

  public GeneratorException(@DialogMessage String message, Throwable cause) {
    super(message, cause);
  }
}
