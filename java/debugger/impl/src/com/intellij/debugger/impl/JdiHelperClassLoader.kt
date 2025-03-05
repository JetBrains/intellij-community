// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.impl

import com.intellij.debugger.engine.evaluation.EvaluateException
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.extensions.ExtensionPointName.Companion.create
import com.sun.jdi.ClassType

/**
 * This extension point lets reimplement the way helper classes are loaded in the user's process.
 *
 * For example, helper classes in Android must be DEX-ed before defining.
 */
interface JdiHelperClassLoader {
  @Throws(EvaluateException::class)
  fun getHelperClass(cls: Class<*>, evaluationContext: EvaluationContextImpl, vararg additionalClassesToLoad: String): ClassType?

  companion object {
    private val EP_NAME: ExtensionPointName<JdiHelperClassLoader> = create("com.intellij.debugger.jdiHelperClassLoader")

    @JvmStatic
    fun getLoaders(): List<JdiHelperClassLoader> = EP_NAME.extensionList
  }
}
