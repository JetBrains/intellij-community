/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.jetbrains.jps.builders.java.dependencyView;

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
  RECORD_COMPONENT
}
