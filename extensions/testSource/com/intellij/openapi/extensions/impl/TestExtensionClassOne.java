/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.extensions.impl;

/**
 * @author Alexander Kireyev
 */
public class TestExtensionClassOne {
  private String myText;

  public TestExtensionClassOne() {
  }

  public TestExtensionClassOne(String text) {
    myText = text;
  }

  public String getText() {
    return myText;
  }

  public void setText(String text) {
    myText = text;
  }
}
