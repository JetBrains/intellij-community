// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.uiDesigner.compiler;

/**
 * Marker interface for all UI Designer exceptions
 * @author Eugene Zhuravlev
 */
public abstract class UIDesignerException extends Exception {
  UIDesignerException(String message) {
    super(message);
  }

  UIDesignerException(String message, Throwable cause) {
    super(message, cause);
  }
}
