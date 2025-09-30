// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.detekt.rules

import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.CorrectableCodeSmell
import io.gitlab.arturbosch.detekt.api.Debt
import io.gitlab.arturbosch.detekt.api.Entity
import io.gitlab.arturbosch.detekt.api.Issue
import io.gitlab.arturbosch.detekt.api.Rule
import io.gitlab.arturbosch.detekt.api.Severity
import org.jetbrains.kotlin.psi.KtAnnotated
import org.jetbrains.kotlin.psi.KtClassInitializer
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtPrimaryConstructor
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPropertyAccessor
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtSecondaryConstructor
import org.jetbrains.kotlin.psi.KtTypeAlias
import org.jetbrains.kotlin.resolve.ImportPath

/**
 * Reports Jewel API annotations that are not paired with a corresponding ApiStatus annotation.
 *
 * For example, if `@InternalJewelApi` is present, `@ApiStatus.Internal` must also be present. The reverse is also true.
 *
 * Auto-correction will add the missing annotation, but formatting is left to the IDE's formatter/ktfmt.
 */
class MissingApiStatusAnnotationRule(config: Config) : Rule(config) {
    override val issue: Issue =
        Issue(
            javaClass.simpleName,
            Severity.Defect,
            "Reports Jewel API annotations that are not paired with a corresponding ApiStatus annotation.",
            Debt.FIVE_MINS,
        )

    private val annotationPairs =
        listOf("InternalJewelApi" to "ApiStatus.Internal", "ExperimentalJewelApi" to "ApiStatus.Experimental")

    override fun visitClassOrObject(classOrObject: KtClassOrObject) {
        super.visitClassOrObject(classOrObject)
        checkAnnotations(classOrObject)
    }

    override fun visitNamedFunction(function: KtNamedFunction) {
        super.visitNamedFunction(function)
        checkAnnotations(function)
    }

    override fun visitProperty(property: KtProperty) {
        super.visitProperty(property)
        checkAnnotations(property)
    }

    override fun visitTypeAlias(typeAlias: KtTypeAlias) {
        super.visitTypeAlias(typeAlias)
        checkAnnotations(typeAlias)
    }

    override fun visitPrimaryConstructor(constructor: KtPrimaryConstructor) {
        super.visitPrimaryConstructor(constructor)
        checkAnnotations(constructor)
    }

    override fun visitSecondaryConstructor(constructor: KtSecondaryConstructor) {
        super.visitSecondaryConstructor(constructor)
        checkAnnotations(constructor)
    }

    override fun visitClassInitializer(initializer: KtClassInitializer) {
        super.visitClassInitializer(initializer)
        checkAnnotations(initializer)
    }

    override fun visitPropertyAccessor(accessor: KtPropertyAccessor) {
        super.visitPropertyAccessor(accessor)
        checkAnnotations(accessor)
    }

    override fun visitParameter(parameter: KtParameter) {
        super.visitParameter(parameter)
        checkAnnotations(parameter)
    }

    private fun checkAnnotations(annotated: KtDeclaration) {
        for ((jewelAnnotation, apiStatusAnnotation) in annotationPairs) {
            val hasJewelAnnotation = annotated.hasAnnotation(jewelAnnotation)
            val hasApiStatusAnnotation = annotated.hasAnnotation(apiStatusAnnotation)

            // If both or neither in the pair are present, let's continue.
            if (hasJewelAnnotation == hasApiStatusAnnotation) continue

            val (existingAnnotation, missingAnnotation) =
                if (hasJewelAnnotation) {
                    jewelAnnotation to apiStatusAnnotation
                } else {
                    apiStatusAnnotation to jewelAnnotation
                }

            val message =
                "The annotation `@$existingAnnotation` is present, but the required annotation `@$missingAnnotation` is missing."
            report(
                CorrectableCodeSmell(
                    issue = issue,
                    entity = Entity.from(annotated),
                    message = message,
                    autoCorrectEnabled = autoCorrect,
                )
            )

            withAutoCorrect {
                annotated.addAnnotation(missingAnnotation)

                // The keys of this map are the Jewel annotations, and the values are the API status annotations.
                val annotationsMap = annotationPairs.toMap()
                when {
                    annotationsMap.containsKey(missingAnnotation) -> {
                        annotated.containingKtFile.addImportIfNeeded(
                            "org.jetbrains.jewel.foundation.$missingAnnotation"
                        )
                    }
                    annotationsMap.containsValue(missingAnnotation) -> {
                        annotated.containingKtFile.addImportIfNeeded("org.jetbrains.annotations.ApiStatus")
                    }
                }
            }
        }
    }

    private fun KtAnnotated.hasAnnotation(annotationName: String): Boolean =
        annotationEntries.any {
            val name = it.shortName.toString()
            name == annotationName || name == annotationName.removePrefix("ApiStatus.")
        }

    private fun KtDeclaration.addAnnotation(annotationName: String) {
        val factory = KtPsiFactory(project)
        val annotationEntry = factory.createAnnotationEntry("@$annotationName")
        val added = addAnnotationEntry(annotationEntry)
        modifierList?.addAfter(factory.createNewLine(1), added)
    }

    private fun KtFile.addImportIfNeeded(fqName: String) {
        val importList = this.importList ?: return
        if (importList.imports.any { it.importedFqName?.asString() == fqName }) {
            return
        }

        val psiFactory = KtPsiFactory(project)
        val importDirective = psiFactory.createImportDirective(ImportPath.fromString(fqName))
        importList.add(psiFactory.createNewLine(1))
        importList.add(importDirective)
    }
}
