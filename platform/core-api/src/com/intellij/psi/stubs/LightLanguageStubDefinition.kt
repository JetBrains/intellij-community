// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.stubs

import com.intellij.lang.ASTNode
import com.intellij.lang.LighterASTNode
import com.intellij.util.diff.FlyweightCapableTreeStructure

/**
 * Defines stub support for a given language with Light AST.
 *
 * Register via `com.intellij.languageStubDefinition` extension point
 *
 * @see [LightStubElementFactory]
 */
interface LightLanguageStubDefinition: LanguageStubDefinition {
  fun parseContentsLight(chameleon: ASTNode): FlyweightCapableTreeStructure<LighterASTNode>
}