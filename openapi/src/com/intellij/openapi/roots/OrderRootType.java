/*
 * Copyright 2000-2007 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.roots;

/**
 * Root types that can be queried from OrderEntry.
 * @see OrderEntry
 * @author dsl
 */
public class OrderRootType {
  private String myName;

  private OrderRootType(String name) {
    myName = name;
  }

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

  /**
   * External annotations path
   */
  public static final OrderRootType ANNOTATIONS = new OrderRootType("ANNOTATIONS");

  public String name() {
    return myName;
  }

  public static final OrderRootType[] ALL_TYPES = {
    CLASSES, CLASSES_AND_OUTPUT, COMPILATION_CLASSES, SOURCES, JAVADOC, ANNOTATIONS
  };
}
