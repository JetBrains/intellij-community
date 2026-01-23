// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the
// Apache 2.0 license.
package org.jetbrains.jewel.detekt

import com.intellij.core.CoreApplicationEnvironment
import com.intellij.openapi.application.ApplicationManager
import com.intellij.psi.impl.source.tree.TreeCopyHandler
import dev.detekt.api.RuleSet
import dev.detekt.api.RuleSetProvider
import org.jetbrains.jewel.detekt.rules.EqualityMembersRule
import org.jetbrains.jewel.detekt.rules.MissingApiStatusAnnotationRule

class JewelRuleSetProvider : RuleSetProvider {
    init {
        @Suppress("UnstableApiUsage")
        CoreApplicationEnvironment.registerExtensionPoint(
            ApplicationManager.getApplication().extensionArea,
            TreeCopyHandler.EP_NAME,
            TreeCopyHandler::class.java,
        )
    }

    override val ruleSetId: RuleSet.Id = RuleSet.Id("jewel")

    override fun instance(): RuleSet =
        RuleSet(ruleSetId, listOf(::EqualityMembersRule, ::MissingApiStatusAnnotationRule))
}
