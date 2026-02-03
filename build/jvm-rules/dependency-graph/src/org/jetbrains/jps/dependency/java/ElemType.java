// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency.java;

public enum ElemType {
  /** Class, interface (including annotation type), or enum declaration */
  TYPE,

  /** Field declaration (includes enum constants) */
  FIELD,

  /** Method declaration */
  METHOD,

  /** Parameter declaration */
  PARAMETER,

  /** Constructor declaration */
  CONSTRUCTOR,

  /** Local variable declaration */
  LOCAL_VARIABLE,

  /** Annotation type declaration */
  ANNOTATION_TYPE,

  /** Package declaration */
  PACKAGE,

  /**
   * Type parameter declaration
   */
  TYPE_PARAMETER,

  /**
   * Use of a type
   */
  TYPE_USE,

  /**
   * Module declaration.
   */
  MODULE,

  /**
   * Record component
   */
  RECORD_COMPONENT;

  public static ElemType fromOrdinal(int ord) {
    for (ElemType value : values()) {
      if (value.ordinal() == ord) {
        return value;
      }
    }
    return null;
  }
}