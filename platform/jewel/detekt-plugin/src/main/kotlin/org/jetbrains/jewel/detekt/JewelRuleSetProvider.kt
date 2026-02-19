// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the
// Apache 2.0 license.
package org.jetbrains.jewel.detekt

import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.RuleSet
import io.gitlab.arturbosch.detekt.api.RuleSetProvider
import org.jetbrains.jewel.detekt.rules.EqualityMembersRule
import org.jetbrains.jewel.detekt.rules.MissingApiStatusAnnotationRule

class JewelRuleSetProvider : RuleSetProvider {
    override val ruleSetId: String = "jewel"

    override fun instance(config: Config): RuleSet =
        RuleSet(ruleSetId, listOf(EqualityMembersRule(config), MissingApiStatusAnnotationRule(config)))
}
