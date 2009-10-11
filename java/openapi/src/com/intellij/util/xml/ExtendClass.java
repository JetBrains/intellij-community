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

package com.intellij.util.xml;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Annotates DOM methods that return either {@link com.intellij.psi.PsiClass} or
 * {@link com.intellij.util.xml.GenericDomValue}<{@link com.intellij.psi.PsiClass}>.
 * Specifies that the references class should extend some other class (or implement interface).
 * If this doesn't happen, error will appear.
 *
 * @see com.intellij.util.xml.ClassTemplate
 * @author Dmitry Avdeev
 */
@Retention(RetentionPolicy.RUNTIME)
public @interface ExtendClass {

  /**
   * @return qualified name of the base class
   */
  String value() default "";

  /**
   * @return states that the class should be concrete and have public default constructor.
   */
  boolean instantiatable() default true;

  /**
   * @return states that the class implements "decorator" pattern, i.e. it should have constructor with
   * one parameter of the same type
   */
  boolean canBeDecorator() default false;

  boolean allowEmpty() default false;

  boolean allowNonPublic() default false;

  boolean allowAbstract() default true;

  boolean allowInterface() default true;

  boolean allowEnum() default true;

  boolean jvmFormat() default true;
}
