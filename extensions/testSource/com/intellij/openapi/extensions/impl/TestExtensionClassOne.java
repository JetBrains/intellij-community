/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.extensions.impl;

import com.thoughtworks.xstream.annotations.XStreamAlias;

/**
 * @author Alexander Kireyev
 */
public class TestExtensionClassOne {
  @XStreamAlias("text")
  private String myText;

  public TestExtensionClassOne() {
  }

  public TestExtensionClassOne(String text) {
    myText = text;
  }

  public String getText() {
    return myText;
  }
}
