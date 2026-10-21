package org.jetbrains.jewel.scripts.bazel.detekt.rules

import dev.detekt.api.Config
import dev.detekt.api.RuleSetProvider
import java.util.ServiceLoader
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.jewel.detekt.JewelRuleSetProvider
import org.jetbrains.jewel.detekt.rules.EqualityMembersRule
import org.jetbrains.jewel.detekt.rules.MissingApiStatusAnnotationRule
import org.junit.jupiter.api.Test

class JewelRuleSetProviderTest {
    @Test
    fun `JewelRuleSetProvider is discovered via ServiceLoader`() {
        val providers =
            ServiceLoader.load(RuleSetProvider::class.java, JewelRuleSetProvider::class.java.classLoader).toList()

        assertThat(providers).anyMatch { it is JewelRuleSetProvider }
    }

    @Test
    fun `rule set contains expected rules`() {
        val provider =
            ServiceLoader.load(RuleSetProvider::class.java, JewelRuleSetProvider::class.java.classLoader)
                .filterIsInstance<JewelRuleSetProvider>()
                .single()

        val ruleSet = provider.instance()

        assertThat(ruleSet.id.value).isEqualTo("jewel")
        val ruleTypes = ruleSet.rules.values.map { it(Config.empty) }.map { it::class }
        assertThat(ruleTypes)
            .containsExactlyInAnyOrder(EqualityMembersRule::class, MissingApiStatusAnnotationRule::class)
    }
}
