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
package com.intellij.lang.jvm.actions

import com.intellij.lang.jvm.JvmClass
import com.intellij.lang.jvm.JvmModifier
import com.intellij.lang.jvm.JvmParameter
import com.intellij.lang.jvm.JvmTypeParameter
import com.intellij.lang.jvm.types.JvmType
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
sealed class MemberRequest {

  companion object {

    @JvmStatic
    fun constructorRequest(targetClass: JvmClass, parameters: List<JvmParameter>) =
      Constructor(targetClass = targetClass, parameters = parameters)

    @JvmStatic
    fun simpleMethodRequest(containingClass: JvmClass,
                            methodName: String,
                            modifier: List<JvmModifier>,
                            returnType: JvmType,
                            parameters: List<JvmParameter>) =
      Method(name = methodName,
             modifiers = modifier,
             targetClass = containingClass,
             returnType = returnType,
             parameters = parameters)

    @JvmStatic
    fun simpleMethodRequest(containingClass: JvmClass,
                            methodName: String,
                            modifier: JvmModifier,
                            returnType: JvmType,
                            parameters: List<JvmParameter>) =
      simpleMethodRequest(containingClass, methodName, listOf(modifier), returnType, parameters)


  }


  class Method(
    val targetClass: JvmClass,
    val name: String,
    val modifiers: List<JvmModifier> = emptyList(),
    val typeParameters: List<JvmTypeParameter> = emptyList(),
    val returnType: JvmType,
    val parameters: List<JvmParameter> = emptyList(),
    val isAbstract: Boolean = false
  ) : MemberRequest()

  class Constructor(
    val targetClass: JvmClass,
    val modifiers: List<JvmModifier> = emptyList(),
    val typeParameters: List<JvmTypeParameter> = emptyList(),
    val parameters: List<JvmParameter> = emptyList()
  ) : MemberRequest()

}
