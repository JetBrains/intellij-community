/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.compiler.make;

import org.jetbrains.annotations.NonNls;

/**
 * @author Eugene Zhuravlev
 *         Date: Apr 7, 2004
 */
public interface AnnotationTargets {
  /** Class, interface or enum declaration */
  int TYPE = 0x1;

  @NonNls String TYPE_STR = "TYPE";

  /** Field declaration (includes enum constants) */
  int FIELD = 0x2;

  @NonNls String FIELD_STR = "FIELD";

  /** Method declaration */
  int METHOD = 0x4;

  @NonNls String METHOD_STR = "METHOD";

  /** Parameter declaration */
  int PARAMETER = 0x8;

  @NonNls String PARAMETER_STR = "PARAMETER";

  /** Constructor declaration */
  int CONSTRUCTOR = 0x10;

  @NonNls String CONSTRUCTOR_STR = "CONSTRUCTOR";

  /** Local variable declaration */
  int LOCAL_VARIABLE = 0x20;

  @NonNls String LOCAL_VARIABLE_STR = "LOCAL_VARIABLE";

  /** Annotation type declaration */
  int ANNOTATION_TYPE = 0x40;

  @NonNls String ANNOTATION_TYPE_STR = "ANNOTATION_TYPE";

  /** Package declaration */
  int PACKAGE = 0x80;

  @NonNls String PACKAGE_STR = "PACKAGE";

  int ALL = TYPE | FIELD | METHOD | PARAMETER | CONSTRUCTOR | LOCAL_VARIABLE | ANNOTATION_TYPE | PACKAGE;
}
