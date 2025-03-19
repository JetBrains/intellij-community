// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("missingOptionalPropertiesSearch")

package com.jetbrains.jsonSchema.impl.fixes

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.util.concurrency.annotations.RequiresReadLock
import com.jetbrains.jsonSchema.extension.JsonLikePsiWalker
import com.jetbrains.jsonSchema.ide.JsonSchemaService
import com.jetbrains.jsonSchema.impl.JsonComplianceCheckerOptions
import com.jetbrains.jsonSchema.impl.JsonSchemaAnnotatorChecker
import com.jetbrains.jsonSchema.impl.JsonValidationError
import com.jetbrains.jsonSchema.impl.JsonValidationError.*

@RequiresReadLock
fun collectMissingPropertiesFromSchema(objectNodePointer: SmartPsiElementPointer<out PsiElement>,
                                       project: Project): JsonSchemaPropertiesInfo? {
  val objectNode = objectNodePointer.dereference() ?: return null
  val schemaObjectFile = JsonSchemaService.Impl.get(project).getSchemaObject(objectNode.containingFile) ?: return null
  val psiWalker = JsonLikePsiWalker.getWalker(objectNode, schemaObjectFile) ?: return null
  val position = psiWalker.findPosition(objectNode, true) ?: return null
  val valueAdapter = psiWalker.createValueAdapter(objectNode) ?: return null
  val checker = JsonSchemaAnnotatorChecker(project, JsonComplianceCheckerOptions(false, false, true))
  checker.checkObjectBySchemaRecordErrors(schemaObjectFile, valueAdapter, position)
  val errorsForNode = checker.errors[objectNode] ?: return null

  val missingRequiredProperties = extractPropertiesOfKind(errorsForNode, FixableIssueKind.MissingProperty)
  val missingKnownProperties = extractPropertiesOfKind(errorsForNode, FixableIssueKind.MissingOptionalProperty)
  return JsonSchemaPropertiesInfo(missingRequiredProperties, missingKnownProperties)
}

private fun extractPropertiesOfKind(foundError: JsonValidationError,
                                    kind: FixableIssueKind): MissingMultiplePropsIssueData {
  val issueData = foundError.takeIf { it.fixableIssueKind == kind }?.issueData

  val filteredProperties = when (issueData) {
    is MissingMultiplePropsIssueData -> filterOutUnwantedProperties(issueData.myMissingPropertyIssues)
    is MissingPropertyIssueData -> filterOutUnwantedProperties(listOf(issueData))
    else -> emptyList()
  }
  return MissingMultiplePropsIssueData(filteredProperties)
}

private fun filterOutUnwantedProperties(missingProperties: Collection<MissingPropertyIssueData>): Collection<MissingPropertyIssueData> {
  return missingProperties.filter { !it.propertyName.startsWith("$") }
}

data class JsonSchemaPropertiesInfo(
  val missingRequiredProperties: MissingMultiplePropsIssueData,
  val missingKnownProperties: MissingMultiplePropsIssueData
) {
  val hasOnlyRequiredPropertiesMissing: Boolean
    get() = missingRequiredProperties.myMissingPropertyIssues.size == missingKnownProperties.myMissingPropertyIssues.size

  val hasNoRequiredPropertiesMissing: Boolean
    get() = missingRequiredProperties.myMissingPropertyIssues.isEmpty()
}
