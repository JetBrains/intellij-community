// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("missingNotRequiredPropertiesSearch")

package com.jetbrains.jsonSchema.impl.fixes

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.util.concurrency.annotations.RequiresReadLock
import com.jetbrains.jsonSchema.extension.JsonLikePsiWalker
import com.jetbrains.jsonSchema.ide.JsonSchemaService
import com.jetbrains.jsonSchema.impl.JsonComplianceCheckerOptions
import com.jetbrains.jsonSchema.impl.JsonSchemaAnnotatorChecker
import com.jetbrains.jsonSchema.impl.JsonValidationError
import com.jetbrains.jsonSchema.impl.JsonValidationError.*

@RequiresReadLock
fun collectMissingPropertiesFromSchema(objectNode: PsiElement,
                                       project: Project): JsonSchemaPropertiesInfo? {
  val schemaObjectFile = JsonSchemaService.Impl.get(project).getSchemaObject(objectNode.containingFile) ?: return null
  val psiWalker = JsonLikePsiWalker.getWalker(objectNode, schemaObjectFile) ?: return null
  val position = psiWalker.findPosition(objectNode, true) ?: return null
  val checker = JsonSchemaAnnotatorChecker(project, JsonComplianceCheckerOptions(false, false, true))
  val valueAdapter = psiWalker.createValueAdapter(objectNode) ?: return null
  checker.checkObjectBySchemaRecordErrors(schemaObjectFile, valueAdapter, position)

  val errorsByKind = checker.errors.values.groupBy { it.fixableIssueKind }
  val missingRequiredProperties = extractPropertiesOfKind(errorsByKind, FixableIssueKind.MissingProperty)
  val missingKnownProperties = extractPropertiesOfKind(errorsByKind, FixableIssueKind.MissingNotRequiredProperty)
  return JsonSchemaPropertiesInfo(missingRequiredProperties, missingKnownProperties)
}

private fun extractPropertiesOfKind(errorsByKind: Map<FixableIssueKind, List<JsonValidationError>>,
                                    kind: FixableIssueKind): MissingMultiplePropsIssueData {
  return errorsByKind[kind]
    .orEmpty()
    .asSequence()
    .map(JsonValidationError::getIssueData)
    .flatMap { issueData ->
      when (issueData) {
        is MissingMultiplePropsIssueData -> issueData.myMissingPropertyIssues.asSequence()
        is MissingPropertyIssueData -> sequenceOf(issueData)
        else -> emptySequence()
      }
    }
    .toList()
    .let { MissingMultiplePropsIssueData(it) }
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
