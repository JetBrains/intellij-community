// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.util

import com.intellij.lang.ParserDefinition
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.TokenSet
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
internal object DefaultCrcCalculator : AbstractCrcCalculator() {
  override fun isApplicable(systemId: ProjectSystemId, file: VirtualFile): Boolean = true

  override fun isIgnoredToken(tokenType: IElementType, tokenText: CharSequence, parserDefinition: ParserDefinition): Boolean {
    val ignoredTokens = TokenSet.orSet(parserDefinition.commentTokens, parserDefinition.whitespaceTokens)
    return ignoredTokens.contains(tokenType)
  }
}