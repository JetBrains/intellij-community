// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the
// Apache 2.0 license.
package org.jetbrains.jewel.detekt.rules

import io.gitlab.arturbosch.detekt.api.CodeSmell
import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.Debt
import io.gitlab.arturbosch.detekt.api.Entity
import io.gitlab.arturbosch.detekt.api.Issue
import io.gitlab.arturbosch.detekt.api.Rule
import io.gitlab.arturbosch.detekt.api.Severity
import io.gitlab.arturbosch.detekt.api.config
import kotlin.collections.orEmpty
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.psiUtil.findDescendantOfType

class EqualityMembersRule(config: Config) : Rule(config) {
    override val issue: Issue =
        Issue(javaClass.simpleName, Severity.Defect, "This rule detects missing equality functions.", Debt.FIVE_MINS)

    private val functionsToCheck: List<String> by config(defaultValue = listOf("equals", "hashCode", "toString"))
    private val annotated: List<String> by config(defaultValue = listOf("GenerateDataFunctions"))

    override fun visitClass(klass: KtClass) {
        super.visitClass(klass)

        if (annotated.isNotEmpty() && !hasAnnotations(klass, annotated.toSet())) {
            return
        }

        val properties = klass.getConstructorPropertiesNames()
        if (properties.isEmpty()) return
        val methodsToRegenerate = analyseEqualityMembers(klass, functionsToCheck.toSet(), properties)

        withAutoCorrect { generateEqualityMembers(klass, methodsToRegenerate, properties) }
    }

    private fun hasAnnotations(klass: KtClass, annotations: Set<String>): Boolean {
        var foundAny = false
        for (annotation in annotations) {
            if (klass.annotationEntries.any { it.shortName.toString() == annotation }) {
                foundAny = true
            }
        }
        return foundAny
    }

    private fun KtClass.getConstructorPropertiesNames(): Set<String> {
        return primaryConstructor
            ?.valueParameters
            ?.filter { it.hasValOrVar() }
            ?.mapNotNull { it.name }
            ?.toSet()
            .orEmpty()
    }

    private fun analyseEqualityMembers(klass: KtClass, equalityMembers: Set<String>, props: Set<String>): Set<String> {
        val functions =
            klass.declarations.filterIsInstance<KtNamedFunction>().filter { equalityMembers.contains(it.name) }
        val badFunctions = mutableSetOf<String>()
        val diff = equalityMembers - functions.mapNotNull { it.name }.toSet()
        badFunctions.addAll(diff)

        if (diff.isNotEmpty()) {
            report(
                CodeSmell(
                    issue,
                    Entity.from(klass),
                    "${klass.name} Missing required equality functions ${diff.joinToString(", ")}.",
                )
            )
        }

        for (function in functions) {
            var hasAll = true
            for (prop in props) {
                val reference =
                    function.bodyExpression?.findDescendantOfType<KtNameReferenceExpression> {
                        it.getReferencedName() == prop
                    }
                if (reference == null) {
                    hasAll = false
                    report(
                        CodeSmell(issue, Entity.from(function), "Function ${function.name} is missing property $prop.")
                    )
                }
            }
            function.name?.let { name ->
                if (!hasAll) {
                    badFunctions.add(name)
                }
            }
        }

        return badFunctions
    }

    private fun generateEqualityMembers(klass: KtClass, functionsToRegenerate: Set<String>, properties: Set<String>) {
        for (function in functionsToRegenerate) {
            val existingFunction = klass.declarations.find { it is KtNamedFunction && it.name == function }
            existingFunction?.delete()
            generateEqualityMember(klass, function, properties)
        }
    }

    private fun generateEqualsFunction(klass: KtClass, name: String, props: Set<String>) {
        val first = props.first()
        val others = props.drop(1)

        generateFunction(klass) {
            appendLine("override fun $name(other: Any?): Boolean {")
            if (others.isNotEmpty()) {
                appendLine("var result = $first.$name()")
                for (other in others) {
                    appendLine("result = 31 * result + ($other?.$name() ?: 0)")
                }
                appendLine("return result")
            } else {
                appendLine("return $first.$name()")
            }
            appendLine("}")
        }
    }

    private fun generateHashCodeFunction(klass: KtClass, name: String, props: Set<String>) {
        generateFunction(klass) {
            appendLine("override fun $name(): Int {")
            appendLine("if (others.isNotEmpty()) {")
            appendLine("if (this === other) return true")
            appendLine("javaClass != other?.javaClass")
            appendLine("other as ${klass.name}")
            for (prop in props) {
                appendLine("if ($prop != other.$prop) return false")
            }
            appendLine("return true")
            appendLine("}")
        }
    }

    private fun generateToStringFunction(klass: KtClass, name: String, props: Set<String>) {
        generateFunction(klass) {
            appendLine("override fun $name(): String {")
            append("return \"${klass.name}(")
            append(props.joinToString(", ") { "$it=\$$it" })
            appendLine("\")")
            appendLine("}")
        }
    }

    private fun generateFunction(klass: KtClass, builder: StringBuilder.() -> Unit) {
        val factory = KtPsiFactory(klass.project)
        klass.addDeclaration(factory.createFunction(buildString(builder)))
    }

    private fun generateEqualityMember(klass: KtClass, functionName: String, properties: Set<String>) {
        when (functionName) {
            "hashCode" -> generateHashCodeFunction(klass, functionName, properties)
            "equals" -> generateEqualsFunction(klass, functionName, properties)
            "toString" -> generateToStringFunction(klass, functionName, properties)
            else -> error("Unable to generate equality member $functionName")
        }
    }
}
