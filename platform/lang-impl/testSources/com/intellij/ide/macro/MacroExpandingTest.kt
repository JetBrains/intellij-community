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

  fun testPrompt() {
    assertEquals("\$Prompt", expand("\$Prompt"))
    assertEquals("Prompt null null", expand("\$Prompt\$"))
    assertEquals("Prompt Title null", expand("\$Prompt:Title\$"))
    assertEquals("Prompt Title value", expand("\$Prompt:Title:value\$"))
  }

  private fun expand(input: String) = MacroManager.expandMacros(input, listOf(regular, withParams, prompt)) {
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

  private val prompt: Macro = object : PromptingMacro() {
    override fun getName() = "Prompt"
    override fun getDescription() = ""
    override fun expand(dataContext: DataContext, vararg args: String?) = args.firstOrNull()
    override fun promptUser(dataContext: DataContext, label: String?, defaultValue: String?) = "Prompt $label $defaultValue"
  }
}