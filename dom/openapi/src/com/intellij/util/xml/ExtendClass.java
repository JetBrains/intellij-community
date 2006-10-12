/*
 * Copyright 2000-2006 JetBrains s.r.o.
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
 *
 */

package com.intellij.util.xml;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * @author Dmitry Avdeev
 */
@Retention(RetentionPolicy.RUNTIME)
public @interface ExtendClass {

  /**
   * Full name of the base class
   */
  String value() default "java.lang.Object";

  /**
   * States that the class should be concrete and have public default constructor.
   */
  boolean instantiatable() default true;

/**
 * States that the class implements "decorator" pattern, i.e. it should have constructor with
 * one parameter of the same type  
 */
  boolean canBeDecorator() default false;
}
