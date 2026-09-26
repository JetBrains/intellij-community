// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the
// Apache 2.0 license.
package org.jetbrains.jewel.detekt

import com.intellij.core.CoreApplicationEnvironment
import com.intellij.openapi.application.ApplicationManager
import com.intellij.psi.impl.source.tree.TreeCopyHandler
import dev.detekt.api.RuleSet
import dev.detekt.api.RuleSetId
import dev.detekt.api.RuleSetProvider
import org.jetbrains.jewel.detekt.rules.EqualityMembersRule
import org.jetbrains.jewel.detekt.rules.MissingApiStatusAnnotationRule

/** Registers Jewel's custom Detekt rules under the `jewel` rule set ID. */
class JewelRuleSetProvider : RuleSetProvider {
    override val ruleSetId: RuleSetId = RuleSetId("jewel")

    init {
        // Detekt's standalone Analysis API does not register treeCopyHandler (it was intentionally removed in
        // https://github.com/detekt/detekt/commit/ba593207d90e97c96c890740a8a3cf3d0978aee2 as KtLint was phasing
        // it out). However, our autocorrect rules use high-level PSI manipulation APIs (e.g. addDeclaration) that
        // require cross-tree node copies, which internally look up this extension point. We register it here at the
        // earliest point in the Detekt plugin lifecycle so that it is available before any analysis begins.
        // The registration must happen here rather than in individual rules, as the RuleSetProvider is loaded via
        // ServiceLoader before the Analysis API session is created.
        val extensionArea = ApplicationManager.getApplication().extensionArea
        if (!extensionArea.hasExtensionPoint(TreeCopyHandler.EP_NAME)) {
            CoreApplicationEnvironment.registerExtensionPoint(
                extensionArea,
                TreeCopyHandler.EP_NAME,
                TreeCopyHandler::class.java,
            )
        }
    }

    override fun instance(): RuleSet =
        RuleSet(ruleSetId, rules = listOf(::EqualityMembersRule, ::MissingApiStatusAnnotationRule))
}
