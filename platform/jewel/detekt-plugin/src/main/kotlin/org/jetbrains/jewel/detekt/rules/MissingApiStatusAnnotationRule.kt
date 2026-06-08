// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.detekt.rules

import dev.detekt.api.Config
import dev.detekt.api.Entity
import dev.detekt.api.Finding
import dev.detekt.api.Rule
import dev.detekt.api.internal.AutoCorrectable
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

private const val RULE_DESCRIPTION =
    "Reports Jewel API annotations that are not paired with a corresponding ApiStatus annotation."

/**
 * Reports Jewel API annotations that are not paired with a corresponding ApiStatus annotation.
 *
 * For example, if `@InternalJewelApi` is present, `@ApiStatus.Internal` must also be present. The reverse is also true.
 *
 * Auto-correction will add the missing annotation, but formatting is left to the IDE's formatter/ktfmt.
 */
@AutoCorrectable(since = "0.38.0")
class MissingApiStatusAnnotationRule(config: Config) : Rule(config, RULE_DESCRIPTION) {
    private val annotationsMap =
        mapOf("InternalJewelApi" to "ApiStatus.Internal", "ExperimentalJewelApi" to "ApiStatus.Experimental")

    override fun visitClassOrObject(classOrObject: KtClassOrObject) {
        super.visitClassOrObject(classOrObject)
        if (!classOrObject.isJewelSymbol()) return

        checkAnnotations(classOrObject)
    }

    override fun visitNamedFunction(function: KtNamedFunction) {
        super.visitNamedFunction(function)
        if (!function.isJewelSymbol()) return
        checkAnnotations(function)
    }

    override fun visitProperty(property: KtProperty) {
        super.visitProperty(property)
        if (!property.isJewelSymbol()) return
        checkAnnotations(property)
    }

    override fun visitTypeAlias(typeAlias: KtTypeAlias) {
        super.visitTypeAlias(typeAlias)
        if (!typeAlias.isJewelSymbol()) return
        checkAnnotations(typeAlias)
    }

    override fun visitPrimaryConstructor(constructor: KtPrimaryConstructor) {
        super.visitPrimaryConstructor(constructor)
        if (!constructor.isJewelSymbol()) return
        checkAnnotations(constructor)
    }

    override fun visitSecondaryConstructor(constructor: KtSecondaryConstructor) {
        super.visitSecondaryConstructor(constructor)
        if (!constructor.isJewelSymbol()) return
        checkAnnotations(constructor)
    }

    override fun visitClassInitializer(initializer: KtClassInitializer) {
        super.visitClassInitializer(initializer)
        if (!initializer.isJewelSymbol()) return
        checkAnnotations(initializer)
    }

    override fun visitPropertyAccessor(accessor: KtPropertyAccessor) {
        super.visitPropertyAccessor(accessor)
        if (!accessor.isJewelSymbol()) return
        checkAnnotations(accessor)
    }

    override fun visitParameter(parameter: KtParameter) {
        super.visitParameter(parameter)
        if (!parameter.isJewelSymbol()) return
        checkAnnotations(parameter)
    }

    private fun checkAnnotations(annotated: KtDeclaration) {
        val violations = mutableListOf<Pair<String, String>>() // existingAnnotation to missingAnnotation

        for ((jewelAnnotation, apiStatusAnnotation) in annotationsMap) {
            val hasJewelAnnotation = annotated.hasAnnotation(jewelAnnotation)
            val hasApiStatusAnnotation = annotated.hasAnnotation(apiStatusAnnotation)

            // If both or neither in the pair are present, let's continue.
            if (hasJewelAnnotation == hasApiStatusAnnotation) continue

            violations +=
                if (hasJewelAnnotation) {
                    jewelAnnotation to apiStatusAnnotation
                } else {
                    apiStatusAnnotation to jewelAnnotation
                }
        }

        val entity = if (violations.isNotEmpty()) Entity.from(annotated) else return
        for ((existingAnnotation, missingAnnotation) in violations) {
            val message =
                "The annotation `@$existingAnnotation` is present, but the required annotation `@$missingAnnotation` is missing."
            report(Finding(entity = entity, message = message))
        }

        if (autoCorrect) {
            for ((_, missingAnnotation) in violations) {
                annotated.addAnnotation(missingAnnotation)
                when {
                    annotationsMap.containsKey(missingAnnotation) ->
                        annotated.containingKtFile.addImportIfNeeded(
                            "org.jetbrains.jewel.foundation.$missingAnnotation"
                        )
                    annotationsMap.containsValue(missingAnnotation) ->
                        annotated.containingKtFile.addImportIfNeeded("org.jetbrains.annotations.ApiStatus")
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
        val importDirective = psiFactory.createImportDirective(importPath = ImportPath.fromString(fqName))
        importList.add(psiFactory.createNewLine(1))
        importList.add(importDirective)
    }
}
