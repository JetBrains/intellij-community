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
package com.intellij.lang.java.actions

import com.intellij.lang.java.JavaLanguage
import com.intellij.lang.jvm.JvmClass
import com.intellij.lang.jvm.JvmModifier
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiModifier
import com.intellij.psi.PsiModifier.ModifierConstant
import com.intellij.psi.PsiTypeParameter
import com.intellij.psi.impl.compiled.ClsClassImpl

@ModifierConstant
fun JvmModifier.toPsi(): String = when (this) {
  JvmModifier.PUBLIC -> PsiModifier.PUBLIC
  JvmModifier.PROTECTED -> PsiModifier.PROTECTED
  JvmModifier.PRIVATE -> PsiModifier.PRIVATE
  JvmModifier.PACKAGE_LOCAL -> PsiModifier.PACKAGE_LOCAL
  JvmModifier.STATIC -> PsiModifier.STATIC
  JvmModifier.ABSTRACT -> PsiModifier.ABSTRACT
  JvmModifier.FINAL -> PsiModifier.FINAL
  JvmModifier.DEFAULT -> PsiModifier.DEFAULT
  JvmModifier.NATIVE -> PsiModifier.NATIVE
  JvmModifier.SYNCHRONIZED -> PsiModifier.NATIVE
  JvmModifier.STRICTFP -> PsiModifier.STRICTFP
  JvmModifier.TRANSIENT -> PsiModifier.TRANSIENT
  JvmModifier.VOLATILE -> PsiModifier.VOLATILE
  JvmModifier.TRANSITIVE -> PsiModifier.TRANSITIVE
}

/**
 * Compiled classes, type parameters are not considered classes.
 *
 * @return Java PsiClass or `null` if the receiver is not a Java PsiClass
 */
fun JvmClass.toJavaClassOrNull(): PsiClass? {
  if (this !is PsiClass) return null
  if (this is PsiTypeParameter) return null
  if (this is ClsClassImpl) return null
  if (this.language != JavaLanguage.INSTANCE) return null
  return this
}
