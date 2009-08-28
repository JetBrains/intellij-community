/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.extensions.impl;

/**
 * @author Alexander Kireyev
 */
public class NonCreatableClass {
  static {
    if (true) {
      throw new RuntimeException("Cannot be created");
    }
  }

  public NonCreatableClass() {
    throw new RuntimeException("Cannot be created");
  }
}
