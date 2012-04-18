package org.jetbrains.ether.dependencyView;

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
  PACKAGE
}
