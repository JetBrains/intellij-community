// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.script;

public final class IdeScriptException extends Exception {
  public IdeScriptException(Throwable cause) {
    super(cause);
  }

  public IdeScriptException(String message, Throwable cause) {
    super(message, cause);
  }

  public IdeScriptException(String message) {
    super(message);
  }

  public IdeScriptException() {
  }
}
