// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the
// Apache 2.0 license.
package org.jetbrains.jewel.detekt

import dev.detekt.api.RuleSet
import dev.detekt.api.RuleSetId
import dev.detekt.api.RuleSetProvider
import org.jetbrains.jewel.detekt.rules.EqualityMembersRule
import org.jetbrains.jewel.detekt.rules.MissingApiStatusAnnotationRule

class JewelRuleSetProvider : RuleSetProvider {
    override val ruleSetId: RuleSetId = RuleSetId("jewel")

    override fun instance(): RuleSet =
        RuleSet(ruleSetId, rules = listOf(::EqualityMembersRule, ::MissingApiStatusAnnotationRule))
}
