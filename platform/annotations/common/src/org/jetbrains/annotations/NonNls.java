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
 * Specifies that an element of the program is not an user-visible string which needs to be localized,
 * or does not contain such strings. This annotation is intended to be used by localization tools for
 * detecting strings which should not be reported as requiring localization.
 * <ul>
 * <li>If a method parameter is annotated with <code>NonNls</code>, the strings passed
 * as values of this parameter are not reported as requiring localization.
 * Also, if the parameter of a property setter method is annotated with <code>NonNls</code>, values
 * of that property in UI Designer forms are never highlighted as hard-coded strings.</li>
 * <li>If a field is annotated with <code>NonNls</code>, all string literals found in the
 * initializer of the field are not reported as requiring localization.</li>
 * <li>If a method is called on a field, parameter or local variable annotated with <code>NonNls</code>,
 * string literals passed as parameters to the method are not reported as requiring localization.
 * <li>If a field, parameter or local variable annotated with <code>NonNls</code> is passed as a
 * parameter to the <code>equals()</code> method invoked on a string literal, the literal is not
 * reported as requiring localization.</li>
 * <li>If a field, parameter or local variable annotated with <code>NonNls</code> is found at
 * the left side of an assignment expression, all string literals in the right side
 * of the expression are not reported as requiring localization.</li>
 * <li>If a method is annotated with <code>NonNls</code>, string literals returned from the method
 * are not reported as requiring localization.</li>
 * <li>If a class is annotated with <code>NonNls</code>, all string literals in
 * the class and all its subclasses are not reported as requiring localization.</li>
 * <li>If a package is annotated with <code>NonNls</code>, all string literals in
 * the package and all its subpackages are not reported as requiring localization.</li>
 * </ul>
 *
 * @author max
 * @see Nls
 */
@Documented
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.METHOD, ElementType.FIELD, ElementType.PARAMETER, ElementType.LOCAL_VARIABLE, ElementType.TYPE, ElementType.PACKAGE})
public @interface NonNls {

}
