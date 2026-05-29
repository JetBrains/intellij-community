// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.detekt

import com.intellij.core.CoreApplicationEnvironment
import com.intellij.openapi.application.ApplicationManager
import com.intellij.psi.impl.source.tree.TreeCopyHandler
import dev.detekt.api.Finding
import dev.detekt.api.Rule
import dev.detekt.test.FakeLanguageVersionSettings
import dev.detekt.test.utils.compileContentForTest
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.ObjectAssert
import org.intellij.lang.annotations.Language

@Suppress("UnstableApiUsage")
internal fun ensurePsiMutationSupported() {
    val extensionArea = ApplicationManager.getApplication().extensionArea
    if (!extensionArea.hasExtensionPoint(TreeCopyHandler.EP_NAME)) {
        CoreApplicationEnvironment.registerExtensionPoint(
            extensionArea,
            TreeCopyHandler.EP_NAME,
            TreeCopyHandler::class.java,
        )
    }
}

internal fun Rule.lintAndFix(@Language("kotlin") code: String): Pair<List<Finding>, String> {
    val ktFile = compileContentForTest(code)
    ensurePsiMutationSupported()
    val findings = visitFile(ktFile, FakeLanguageVersionSettings())
    return findings to ktFile.text
}

internal fun ObjectAssert<Finding>.hasMessage(message: String) =
    satisfies({ assertThat(it.message).isEqualTo(message) })
