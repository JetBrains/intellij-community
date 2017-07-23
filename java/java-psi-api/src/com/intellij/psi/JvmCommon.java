/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import org.jetbrains.annotations.ApiStatus;

import java.lang.annotation.*;

/**
 * Annotation for marking {@link PsiElement} subclasses usages that they will be used in
 * language-abstracted way:
 * <ul>
 * <li>No modification methods should be directly called on such instances</li>
 * <li>Usages should be aware of {@link PsiElement#getLanguage()}, and not be hardcoded to dedicated language</li>
 * <li>Instances could be "virtual" and don't have physical representations</li>
 * <li>Complex operations should be performed via corresponding {@link com.intellij.lang.LanguageExtensionPoint} or similar API</li>
 * <li>{@link org.jetbrains.uast.UClass}-like instances should be supported</li>
 * </ul>
 * <b>Note:</b> this annotation considered as kind-of "transitional" until generic language-independent API will be implemented
 */
@ApiStatus.Experimental
@Documented
@Retention(RetentionPolicy.CLASS)
@Target({
  ElementType.TYPE, ElementType.PARAMETER, ElementType.FIELD, ElementType.TYPE_USE, ElementType.TYPE_PARAMETER
})
public @interface JvmCommon {
}
