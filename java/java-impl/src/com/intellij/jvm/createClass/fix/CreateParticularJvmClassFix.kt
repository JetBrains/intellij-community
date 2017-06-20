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
package com.intellij.jvm.createClass.fix

import com.intellij.codeInsight.daemon.QuickFixBundle.message
import com.intellij.jvm.createClass.CreateJvmClassFactory
import com.intellij.jvm.createClass.SourceClassKind
import com.intellij.jvm.createClass.ui.CreateClassUserInfo
import com.intellij.psi.PsiReference

/**
 * Fix with fixed factory & class kind.
 */
internal class CreateParticularJvmClassFix<R : PsiReference>(
  private val myClassKind: SourceClassKind,
  factory: CreateJvmClassFactory,
  private val myBaseFix: BaseCreateJvmClassFix<R>
) : BaseCreateJvmClassFix<R>() {

  override fun getText(): String = message("create.class.from.usage.text", myClassKind.displayName, myClassName)

  override val reference: R? get() = myBaseFix.reference

  override fun getClassName(reference: R) = myBaseFix.getClassName(reference)

  private val myFactories = mapOf(myClassKind to factory)

  override fun getFactories(reference: R) = myFactories

  override fun createRequest(reference: R, userInfo: CreateClassUserInfo) = myBaseFix.createRequest(reference, userInfo)
}
