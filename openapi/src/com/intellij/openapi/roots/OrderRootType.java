/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.roots;

/**
 * Root types that can be queried from OrderEntry.
 * @see com.intellij.openapi.roots.OrderEntry
 * @author dsl
 */
public class OrderRootType {
  private final String myName;

  /**
   * Classpath.
   */
  public static final OrderRootType CLASSES_AND_OUTPUT = new OrderRootType("CLASSES_AND_OUTPUT");

  /**
   * Classpath for compilation
   */
  public static final OrderRootType COMPILATION_CLASSES = new OrderRootType("COMPILATION_CLASSES");

  /**
   * Classpath without output directories for this module.
   */
  public static final OrderRootType CLASSES = new OrderRootType("CLASSES");

  /**
   * Sources.
   */
  public static final OrderRootType SOURCES = new OrderRootType("SOURCES");

  /**
   * JavaDoc paths.
   */
  public static final OrderRootType JAVADOC = new OrderRootType("JAVADOC");

  public static final OrderRootType[] ALL_TYPES = {
    CLASSES, CLASSES_AND_OUTPUT, COMPILATION_CLASSES, SOURCES, JAVADOC
  };

  private OrderRootType(String name) {
    myName = name;
  }

  public String getName() {
    return myName;
  }

  public String toString() {
    return myName;
  }
}
