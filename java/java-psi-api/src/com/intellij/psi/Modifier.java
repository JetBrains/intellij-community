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
package com.intellij.psi;

import org.intellij.lang.annotations.Pattern;

@Pattern(PsiModifier.PUBLIC
           + "|" + PsiModifier.PROTECTED
           + "|" + PsiModifier.PRIVATE
           + "|" + PsiModifier.ABSTRACT
           + "|" + PsiModifier.FINAL
           + "|" + PsiModifier.NATIVE
           + "|" + PsiModifier.PACKAGE_LOCAL
           + "|" + PsiModifier.STATIC
           + "|" + PsiModifier.STRICTFP
           + "|" + PsiModifier.SYNCHRONIZED
           + "|" + PsiModifier.TRANSIENT
           + "|" + PsiModifier.VOLATILE
)
/**
 * Represents Java member modifier.
 * When String method or variable is annotated with this, 
 * the corresponding value must be one of the string constants defined in {@link com.intellij.psi.PsiModifier}
 */
public @interface Modifier {
}
