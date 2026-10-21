// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.scripts.bazel.detekt.rules

import dev.detekt.api.Finding
import dev.detekt.api.Rule
import dev.detekt.api.modifiedText
import dev.detekt.test.FakeLanguageVersionSettings
import dev.detekt.test.utils.compileContentForTest
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.ObjectAssert
import org.intellij.lang.annotations.Language

// Bazel's JewelBaseRule.visit() registers the treeCopyHandler extension point itself (the Gradle
// one relies on something else in the process registering it first), so this copy skips the
// Gradle version's ensurePsiMutationSupported() call.
internal fun Rule.lintAndFix(@Language("kotlin") code: String): Pair<List<Finding>, String> {
    val ktFile = compileContentForTest(code)
    val findings = visitFile(ktFile, FakeLanguageVersionSettings())
    return findings to (ktFile.modifiedText ?: ktFile.text)
}

internal fun ObjectAssert<Finding>.hasMessage(message: String) =
    satisfies({ assertThat(it.message).isEqualTo(message) })
