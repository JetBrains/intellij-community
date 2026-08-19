// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.uiDesigner.lw;

/**
 * The value of an enum-typed property, as it was read from a form file: the name of the enum class and the
 * name of the constant. Deliberately not an {@code Enum} instance - loading the enum class here would run its
 * static initializer, and would bind the value to whichever class loader read the form rather than to the one
 * that owns the component instance the value is applied to.
 *
 * @see LwIntroEnumProperty
 */
public final class EnumDescriptor {
  private final String myClassName;
  private final String myConstantName;

  public EnumDescriptor(final String className, final String constantName) {
    if (className == null) {
      throw new IllegalArgumentException("className cannot be null");
    }
    if (constantName == null) {
      throw new IllegalArgumentException("constantName cannot be null");
    }
    myClassName = className;
    myConstantName = constantName;
  }

  /**
   * @return the enum class name in the {@code com.foo.Outer$Inner} form
   */
  public String getClassName() {
    return myClassName;
  }

  public String getConstantName() {
    return myConstantName;
  }

  @Override
  public String toString() {
    return myConstantName;
  }
}
