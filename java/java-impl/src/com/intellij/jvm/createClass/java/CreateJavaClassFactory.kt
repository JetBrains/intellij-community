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
package com.intellij.jvm.createClass.java

import com.intellij.codeInsight.daemon.impl.quickfix.CreateFromUsageUtils.setupSuperClassReference
import com.intellij.jvm.JvmClassKind
import com.intellij.jvm.createClass.CreateClassRequest
import com.intellij.jvm.createClass.CreateJvmClassFactory
import com.intellij.jvm.createClass.SourceClassKind
import com.intellij.psi.*
import com.intellij.psi.CommonClassNames.JAVA_LANG_ENUM
import com.intellij.psi.util.PsiUtil

class CreateJavaClassFactory : CreateJvmClassFactory {

  private fun getJavaSourceClassKind(it: JvmClassKind): JavaClassKind = when (it) {
    JvmClassKind.CLASS -> JavaClassKind.CLASS
    JvmClassKind.INTERFACE -> JavaClassKind.INTERFACE
    JvmClassKind.ANNOTATION -> JavaClassKind.ANNOTATION
    JvmClassKind.ENUM -> JavaClassKind.ENUM
  }

  override fun getSourceKinds(jvmClassKinds: Collection<JvmClassKind>, context: PsiElement): Collection<SourceClassKind> {
    return jvmClassKinds.map { getJavaSourceClassKind(it) }
  }

  override fun createClass(request: CreateClassRequest): PsiClass {
    val javaClassKind = request.classKind as JavaClassKind
    val directory = request.targetDirectory
    val name = request.className
    val directoryService = JavaDirectoryService.getInstance()

    val targetClass = when (javaClassKind) {
      JavaClassKind.CLASS -> directoryService.createClass(directory, name)
      JavaClassKind.INTERFACE -> directoryService.createInterface(directory, name)
      JavaClassKind.ANNOTATION -> directoryService.createAnnotationType(directory, name)
      JavaClassKind.ENUM -> directoryService.createEnum(directory, name)
    }

    val superClassName = request.superTypeFqn
    if (superClassName != null && (javaClassKind != JavaClassKind.ENUM || superClassName != JAVA_LANG_ENUM)) {
      setupSuperClassReference(targetClass, superClassName)
    }

    setupGenericParameters(targetClass, request.typeArguments)
    return targetClass
  }

  private fun setupGenericParameters(targetClass: PsiClass, typeArguments: List<PsiType?>) {
    val numParams = typeArguments.size
    if (numParams == 0) return
    val typeParameterList = targetClass.typeParameterList ?: return
    val factory = JavaPsiFacade.getElementFactory(targetClass.project)
    val usedNames = mutableSetOf<String>()
    var idx = 0
    for (type in typeArguments) {
      val psiClass = PsiUtil.resolveClassInType(type)
      val initialName = (psiClass as? PsiTypeParameter)?.name ?: if (idx > 0) "T" + idx else "T"
      var paramName = initialName
      while (!usedNames.add(paramName)) {
        idx++
        paramName = "T" + idx
      }
      typeParameterList.add(factory.createTypeParameterFromText(paramName, null))
    }
  }
}
