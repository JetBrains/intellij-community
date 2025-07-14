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
import org.jetbrains.kotlin.com.intellij.psi.PsiWhiteSpace
import org.jetbrains.kotlin.com.intellij.psi.impl.source.tree.LeafPsiElement
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.psiUtil.findDescendantOfType

/**
 * This rule ensures that classes annotated with specific annotations (e.g., `@GenerateDataFunctions`) have correct
 * `equals()`, `hashCode()`, and `toString()` implementations.
 *
 * It checks if these functions are present and if they correctly use all the class's properties. If a function is
 * missing or incorrect, this rule will report a [CodeSmell].
 *
 * This rule also supports auto-correction. It can generate the missing or incorrect functions, ensuring they are
 * correctly implemented based on the class's properties.
 */
class EqualityMembersRule(config: Config) : Rule(config) {
    override val issue: Issue =
        Issue(
            javaClass.simpleName,
            Severity.Defect,
            "This rule detects missing or incomplete equals/hashCode/toString functions.",
            Debt.FIVE_MINS,
        )

    private val functionsToCheck: List<String> by config(defaultValue = listOf("equals", "hashCode", "toString"))
    private val annotated: List<String> by config(defaultValue = listOf("GenerateDataFunctions"))

    override fun visitClass(klass: KtClass) {
        super.visitClass(klass)

        if (annotated.isNotEmpty() && !hasAnnotations(klass, annotated.toSet())) {
            return
        }

        val properties = klass.getConstructorPropertiesNames()
        if (properties.isEmpty()) return
        val methodsToRegenerate = analyzeEqualityMembers(klass, functionsToCheck.toSet(), properties)

        withAutoCorrect { generateEqualityMembers(klass, methodsToRegenerate.sorted(), properties) }
    }

    private fun hasAnnotations(klass: KtClass, requiredAnnotations: Set<String>): Boolean {
        var foundAny = false
        for (annotation in requiredAnnotations) {
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

    private fun analyzeEqualityMembers(
        klass: KtClass,
        expectedFunctionNames: Set<String>,
        declaredCtorPropNames: Set<String>,
    ): Set<String> {
        val declaredFunctions =
            klass.declarations.filterIsInstance<KtNamedFunction>().filter { it.name in expectedFunctionNames }

        val badFunctionNames = mutableSetOf<String>()
        val missingFunctionNames = expectedFunctionNames - declaredFunctions.mapNotNull { it.name }.toSet()
        badFunctionNames += missingFunctionNames

        if (missingFunctionNames.isNotEmpty()) {
            val functionNames = missingFunctionNames.joinToString(", ")
            report(
                CodeSmell(
                    issue = issue,
                    entity = Entity.from(klass),
                    message = "${klass.name} is missing required functions: $functionNames.",
                )
            )
        }

        for (declaredFunction in declaredFunctions) {
            var hasAll = true
            for (prop in declaredCtorPropNames) {
                runCatching {
                        declaredFunction.bodyExpression?.findDescendantOfType<KtNameReferenceExpression> {
                            it.getReferencedName() == prop
                        }
                    }
                    .fold(
                        onSuccess = {
                            if (it == null) {
                                hasAll = false
                                report(
                                    CodeSmell(
                                        issue,
                                        Entity.from(declaredFunction),
                                        "Function ${declaredFunction.name} is missing property $prop.",
                                    )
                                )
                            }
                        },
                        onFailure = {
                            System.err.println("Failed to find property $prop in function ${declaredFunction.name}")
                            it.printStackTrace()
                            throw it
                        },
                    )
            }

            declaredFunction.name?.let { name ->
                if (!hasAll) {
                    badFunctionNames.add(name)
                }
            }
        }

        return badFunctionNames
    }

    private fun generateEqualityMembers(
        klass: KtClass,
        functionsToRegenerate: Collection<String>,
        properties: Set<String>,
    ) {
        for (functionName in functionsToRegenerate) {
            val existingFunction = klass.declarations.find { it is KtNamedFunction && it.name == functionName }
            existingFunction?.delete()
            generateNamedFunction(klass, functionName, properties)
        }

        // Clean up class code after the fixes
        val factory = KtPsiFactory(klass.project)
        val body = checkNotNull(klass.body)
        if (body.lastChild is LeafPsiElement && (body.lastChild as LeafPsiElement).elementType == KtTokens.RBRACE) {
            when (val prevSibling = body.lastChild.prevSibling) {
                is KtFunction -> {
                    // Missing newline before rbrace
                    body.addBefore(factory.createWhiteSpace("\n"), body.lastChild)
                }
                is PsiWhiteSpace -> {
                    // Spurious whitespace before rbrace
                    val text = prevSibling.text
                    if (text.startsWith("\n") && text.endsWith(" ")) {
                        prevSibling.replace(factory.createWhiteSpace("\n"))
                    }
                }
            }
        }
    }

    private fun generateNamedFunction(klass: KtClass, functionName: String, properties: Set<String>) {
        when (functionName) {
            "hashCode" -> generateHashCodeFunction(klass, properties)
            "equals" -> generateEqualsFunction(klass, properties)
            "toString" -> generateToStringFunction(klass, properties)
            else -> error("Unable to generate equality function $functionName")
        }
    }

    private fun generateEqualsFunction(klass: KtClass, props: Set<String>) {
        generateFunction(klass) {
            appendLine("override fun equals(other: Any?): Boolean {")
            appendLine("    if (this === other) return true")
            appendLine("    if (javaClass != other?.javaClass) return false")
            appendLine("")
            appendLine("    other as ${klass.name}")
            appendLine("")
            for (prop in props) {
                appendLine("    if ($prop != other.$prop) return false")
            }
            appendLine("")
            appendLine("    return true")
            appendLine("}")
        }
    }

    private fun generateHashCodeFunction(klass: KtClass, props: Set<String>) {
        val first = props.first()
        val others = props.drop(1)

        generateFunction(klass) {
            appendLine("override fun hashCode(): Int {")
            if (others.isNotEmpty()) {
                appendLine("    var result = $first.hashCode()")
                for (other in others) {
                    appendLine("    result = 31 * result + $other.hashCode()")
                }
                appendLine("    return result")
            } else {
                appendLine("    return $first.hashCode()")
            }
            appendLine("}")
        }
    }

    private fun generateToStringFunction(klass: KtClass, props: Set<String>) {
        generateFunction(klass) {
            appendLine("override fun toString(): String {")
            append("    return \"${klass.name}(")
            append(props.joinToString(", ") { "$it=$$it" })
            appendLine(")\"")
            appendLine("}")
        }
    }

    private fun generateFunction(klass: KtClass, builder: StringBuilder.() -> Unit) {
        val factory = KtPsiFactory(klass.project)
        val newFunction = factory.createFunction(buildString(builder))

        // 1. Add the function itself to the class body
        val addedElement = klass.addDeclaration(newFunction)

        // 2. Add two newlines before the function we just added for nice formatting.
        checkNotNull(klass.body).addBefore(factory.createWhiteSpace("\n\n"), addedElement)
    }
}
