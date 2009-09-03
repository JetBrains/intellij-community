/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.uiDesigner;

/**
 * This class must be in main classloader because of JVM's restrictions (it's used as DataFlavor class)
 *
 * @author yole
 */
public final class SerializedComponentData {
  private final String mySerializedComponents;

  public SerializedComponentData(final String components) {
    mySerializedComponents = components;
  }

  public String getSerializedComponents() {
    return mySerializedComponents;
  }
}