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
package com.intellij.psi.util

import com.intellij.psi.PsiElement
import kotlin.reflect.KClass

inline fun <reified T : PsiElement> PsiElement.parentOfType(): T? = parentOfType(T::class)

fun <T : PsiElement> PsiElement.parentOfType(vararg classes: KClass<out T>): T? {
  return PsiTreeUtil.getParentOfType(this, *classes.map { it.java }.toTypedArray())
}


inline fun <reified T : PsiElement> PsiElement.parentsOfType(): Sequence<T> = parentsOfType(T::class.java)

fun <T : PsiElement> PsiElement.parentsOfType(clazz: Class<out T>): Sequence<T> = parents().filterIsInstance(clazz)

fun PsiElement.parents(): Sequence<PsiElement> = generateSequence(this) { it.parent }
