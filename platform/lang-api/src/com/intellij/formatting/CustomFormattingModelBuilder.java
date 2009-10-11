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

/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Aug 23, 2006
 * Time: 3:56:42 PM
 */
package com.intellij.formatting;

import com.intellij.psi.PsiElement;

public interface CustomFormattingModelBuilder extends FormattingModelBuilder {
  /**
   * Implementors of the method must decide if this particular builder is responsible to format a <code>context</code> passed.
   * @param context a PSI context for the builder to decide if it is responsible to format these kind of things.
   * @return <code>true</code> if this particular builder shall be used to format <code>context</code>
   */
  boolean isEngagedToFormat(PsiElement context);
}