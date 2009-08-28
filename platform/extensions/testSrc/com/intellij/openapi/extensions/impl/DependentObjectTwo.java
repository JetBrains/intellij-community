/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.extensions.impl;

/**
 * @author Alexander Kireyev
 */
public class DependentObjectTwo {
  private final DependentObjectOne myOne;

  public DependentObjectTwo(DependentObjectOne one) {
    myOne = one;
  }

  public DependentObjectOne getOne() {
    return myOne;
  }
}
