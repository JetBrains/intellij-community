// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.uiDesigner.compiler;

public final class FormErrorInfo {
  private String myComponentId;
  private String myErrorMessage;

  public FormErrorInfo(final String componentId, final String errorMessage) {
    myComponentId = componentId;
    myErrorMessage = errorMessage;
  }

  public String getComponentId() {
    return myComponentId;
  }

  public void setComponentId(final String componentId) {
    myComponentId = componentId;
  }

  public String getErrorMessage() {
    return myErrorMessage;
  }

  public void setErrorMessage(final String errorMessage) {
    myErrorMessage = errorMessage;
  }
}
