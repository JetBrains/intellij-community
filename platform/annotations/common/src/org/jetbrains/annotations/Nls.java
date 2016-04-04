/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

package org.jetbrains.annotations;

import java.lang.annotation.*;

/**
 * Specifies that an element of the program is an user-visible string which needs to be localized.
 * This annotation is intended to be used by localization tools for
 * detecting strings which should be reported as requiring localization.
 *
 * @author mike
 * @see NonNls
 */
@Documented
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.METHOD, ElementType.FIELD, ElementType.PARAMETER, ElementType.LOCAL_VARIABLE, ElementType.TYPE, ElementType.PACKAGE})
public @interface Nls {

  enum Capitalization {

    NotSpecified,
    /**
     * e.g. This Is a Title
     */
    Title,
    /**
     * e.g. This is a sentence
     */
    Sentence
  }

  Capitalization capitalization() default Capitalization.NotSpecified;
}
