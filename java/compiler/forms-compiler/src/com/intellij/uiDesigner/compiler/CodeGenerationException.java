// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.uiDesigner.compiler;

public class CodeGenerationException extends UIDesignerException {
  private final String myComponentId;

  public CodeGenerationException(final String componentId, final String message) {
    super(message);
    myComponentId = componentId;
  }

  CodeGenerationException(final String componentId, final String message, final Throwable cause) {
    super(message, cause);
    myComponentId = componentId;
  }

  public String getComponentId() {
    return myComponentId;
  }
}
