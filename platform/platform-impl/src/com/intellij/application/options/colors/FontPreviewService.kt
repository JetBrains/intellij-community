// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.application.options.colors

import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.components.service

/** IDE-specific sample text on the Settings | Editor | Font page */
open class FontPreviewService {
  companion object {
    @JvmStatic
    fun getInstance(): FontPreviewService = service()
  }

  protected val endText: String = """
    Default:
    abcdefghijklmnopqrstuvwxyz
    ABCDEFGHIJKLMNOPQRSTUVWXYZ
    0123456789 (){}[]
    + - * / = .,;:!? #&$%@|^

    <bold>Bold:
    abcdefghijklmnopqrstuvwxyz
    ABCDEFGHIJKLMNOPQRSTUVWXYZ
    0123456789 (){}[]
    + - * / = .,;:!? #&$%@|^</bold>
    
    <!-- -- != := === >= >- >=> |-> -> <$>
    </> #[ |||> |= ~@


  """.trimIndent()

  open val fontPreviewText: String = """
    ${ApplicationNamesInfo.getInstance().fullProductName} is an <bold>Integrated 
    Development Environment (IDE)</bold> designed
    to maximize productivity. It provides
    <bold>clever code completion, static code
    analysis, and refactorings,</bold> and lets
    you focus on the bright side of
    software development making
    it an enjoyable experience.
  """.trimIndent() + "\n\n$endText"
}
