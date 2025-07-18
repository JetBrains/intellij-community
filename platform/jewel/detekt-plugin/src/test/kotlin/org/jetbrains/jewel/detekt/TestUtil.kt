// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.detekt

import io.github.detekt.test.utils.compileContentForTest
import io.gitlab.arturbosch.detekt.api.Finding
import io.gitlab.arturbosch.detekt.api.Rule
import org.intellij.lang.annotations.Language

internal fun Rule.lintAndFix(@Language("kotlin") code: String): Pair<List<Finding>, String> {
    val ktFile = compileContentForTest(code)
    visit(ktFile)
    return findings to ktFile.text
}
