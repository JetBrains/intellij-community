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

package org.jetbrains.annotations;

import java.lang.annotation.*;

/**
 * Specifies that a method parameter accepts arguments which must be valid property
 * keys in a specific resource bundle. When a string literal which is not a property
 * key in the specified bundle is passed as a parameter, IntelliJ IDEA highlights
 * it as an error. The annotation is also used to provide completion in string literals
 * passed as parameters.
 *
 * @author max
 */
@Documented
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.PARAMETER, ElementType.LOCAL_VARIABLE, ElementType.FIELD})
public @interface PropertyKey {
  /**
   * The full-qualified name of the resource bundle in which the property keys must
   * be present. Consists of a full-qualified name of the package corresponding to the
   * directory where the resource bundle is located and the base name of the resource
   * bundle (with no locale specifier or extension), separated with a dot.
   */
  String resourceBundle();
}
