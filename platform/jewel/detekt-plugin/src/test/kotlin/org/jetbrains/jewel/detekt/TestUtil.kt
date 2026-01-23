// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.detekt

import com.intellij.core.CoreApplicationEnvironment
import com.intellij.mock.MockApplication
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Disposer
import com.intellij.psi.impl.source.tree.TreeCopyHandler
import dev.detekt.api.Finding
import dev.detekt.api.Rule
import dev.detekt.api.modifiedText
import dev.detekt.test.lint
import dev.detekt.test.utils.compileContentForTest
import org.intellij.lang.annotations.Language

internal fun Rule.lintAndFix(@Language("kotlin") code: String): Pair<List<Finding>, String> {
    val ktFile = compileContentForTest(code)
    registerTreeCopyHandler()
    return lint(ktFile) to (ktFile.modifiedText ?: ktFile.text)
}

@Suppress("UnstableApiUsage")
internal fun registerTreeCopyHandler() {
    if (ApplicationManager.getApplication() == null) {
        val disposer = Disposer.newDisposable()
        val application = MockApplication(disposer)
        ApplicationManager.setApplication(application, disposer)
    }

    if (!ApplicationManager.getApplication().extensionArea.hasExtensionPoint(TreeCopyHandler.EP_NAME)) {
        CoreApplicationEnvironment.registerExtensionPoint(
            ApplicationManager.getApplication().extensionArea,
            TreeCopyHandler.EP_NAME,
            TreeCopyHandler::class.java,
        )
    }
}
