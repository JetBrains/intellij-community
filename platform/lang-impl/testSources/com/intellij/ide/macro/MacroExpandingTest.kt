// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.macro

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.testFramework.LightPlatformTestCase

class MacroExpandingTest: LightPlatformTestCase() {
  fun testIncomplete() {
    assertEquals("\$Regular", expand("\$Regular"))
    assertEquals("\$Regular(", expand("\$Regular("))
  }

  fun testRegularMacro() {
    assertEquals("foo", expand("\$Regular\$"))
    assertEquals("prefix_foo", expand("prefix_\$Regular\$"))
    assertEquals("foofoofoo", expand("\$Regular\$\$Regular\$\$Regular\$"))
  }

  fun testMacroWithParams() {
    assertEquals("param", expand("\$WithParams(param)\$"))
    assertEquals("", expand("\$WithParams()\$"))
  }

  private fun expand(input: String) = MacroManager.expandMacros(input, listOf(regular, withParams)) {
    macro, occurence -> macro.expandOccurence(DataContext.EMPTY_CONTEXT, occurence)
  }

  private val regular: Macro = object : Macro() {
    override fun getName() = "Regular"
    override fun getDescription() = ""
    override fun expand(dataContext: DataContext) = "foo"
  }

  private val withParams: Macro = object : Macro() {
    override fun getName() = "WithParams"
    override fun getDescription() = ""
    override fun expand(dataContext: DataContext) = "foo"
    override fun expand(dataContext: DataContext, vararg args: String?) = args.firstOrNull()
  }
}