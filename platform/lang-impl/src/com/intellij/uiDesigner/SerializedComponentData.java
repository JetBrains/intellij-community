// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.uiDesigner;

/**
 * This class must be in main classloader because of JVM's restrictions (it's used as DataFlavor class)
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