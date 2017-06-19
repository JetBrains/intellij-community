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

import com.intellij.codeInsight.daemon.impl.quickfix.CreateClassKind
import com.intellij.codeInsight.daemon.impl.quickfix.CreateFromUsageUtils
import com.intellij.jvm.createClass.api.CreateClassAction
import com.intellij.jvm.createClass.api.CreateClassInfo
import com.intellij.psi.PsiClass

class CreateJavaClassAction(override val classKind: JavaClassKind) : CreateClassAction {

  override fun createClass(info: CreateClassInfo): PsiClass {
    val createClassKind = when (classKind) {
      JavaClassKind.ANNOTATION -> CreateClassKind.ANNOTATION
      JavaClassKind.CLASS -> CreateClassKind.CLASS
      JavaClassKind.INTERFACE -> CreateClassKind.INTERFACE
      JavaClassKind.ENUM -> CreateClassKind.ENUM
    }
    return CreateFromUsageUtils.createClass(
      createClassKind,
      info.targetDirectory,
      info.className,
      info.context.manager,
      info.context,
      null, null
    )
  }
}