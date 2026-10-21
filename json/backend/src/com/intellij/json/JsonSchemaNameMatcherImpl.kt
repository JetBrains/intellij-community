// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.json

import com.intellij.json.psi.JsonStringLiteral
import com.intellij.psi.PsiElement

internal class JsonSchemaNameMatcherImpl : JsonSchemaNameMatcher {
  override fun matches(literal: JsonStringLiteral): Boolean = JsonSchemaSpellcheckerClientForJson(literal).matchesNameFromSchema()

  private class JsonSchemaSpellcheckerClientForJson(private val literal: JsonStringLiteral) : JsonSchemaSpellcheckerClient() {
    override fun getElement(): PsiElement = literal

    override fun getValue(): String = literal.value
  }
}
