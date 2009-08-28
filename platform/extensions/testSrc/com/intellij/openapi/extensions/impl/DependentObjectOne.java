/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.extensions.impl;

/**
 * @author Alexander Kireyev
 */
public class DependentObjectOne {
  private final XMLTestBean[] myTestBeans;

  public DependentObjectOne(XMLTestBean[] testBeans) {
    myTestBeans = testBeans;
  }

  public XMLTestBean[] getTestBeans() {
    return myTestBeans;
  }
}
