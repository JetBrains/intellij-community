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
package org.intellij.lang.annotations;

import java.lang.annotation.*;

@Documented
@Retention(RetentionPolicy.SOURCE)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.LOCAL_VARIABLE,
          ElementType.ANNOTATION_TYPE, // to subclass
          ElementType.METHOD
        })
public @interface MagicConstant {
  Class valuesFromClass() default void.class;
  Class flagsFromClass() default void.class; // int constants which can be combined via | (bitwise or) operator. 0 and -1 are considered acceptable values.
  String[] stringValues() default {};
  long[] intValues() default {};
  long[] flags() default {};   // int constants which can be combined via | (bitwise or) operator. 0 and -1 are considered acceptable values.
}
