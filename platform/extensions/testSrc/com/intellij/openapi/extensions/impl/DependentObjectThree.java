/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.extensions.impl;

/**
 * @author Alexander Kireyev
 */
public class DependentObjectThree {
  private final DependentObjectOne myOne;
  private final DependentObjectTwo myTwo;
  private final XMLTestBean[] myTestBeans;

  public DependentObjectThree(DependentObjectOne one, XMLTestBean[] testBeans, DependentObjectTwo two) {
    myOne = one;
    myTestBeans = testBeans;
    myTwo = two;
  }

  public DependentObjectOne getOne() {
    return myOne;
  }

  public XMLTestBean[] getTestBeans() {
    return myTestBeans;
  }

  public DependentObjectTwo getTwo() {
    return myTwo;
  }
}
