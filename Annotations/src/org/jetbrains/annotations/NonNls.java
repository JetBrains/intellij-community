/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
 * If a method parameter is annotated with <code>NonNls</code>, it means that the string passed
 * as the value of this parameter is never displayed to the user and does not need to be localized.
 * If a method is annotated with <code>NonNls</code>, it means that the return value of the method
 * is a string which is never displayed to the user and does not need to be localized.
 * The annotation is intended to be used by localization tools for detecting strings which should
 * not be reported as requiring localization.
 *
 * @author max
 */
@Documented
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.PARAMETER, ElementType.METHOD, ElementType.FIELD, ElementType.LOCAL_VARIABLE})
public @interface NonNls {

}
