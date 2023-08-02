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
import com.jetbrains.jsonSchema.impl.JsonValidationError.FixableIssueKind

@RequiresReadLock
fun collectMissingPropertiesFromSchema(objectNode: PsiElement,
                                       project: Project,
                                       onlyRequired: Boolean): JsonValidationError.MissingMultiplePropsIssueData? {
  val schemaObjectFile = JsonSchemaService.Impl.get(project).getSchemaObject(objectNode.containingFile) ?: return null
  val psiWalker = JsonLikePsiWalker.getWalker(objectNode, schemaObjectFile) ?: return null
  val position = psiWalker.findPosition(objectNode, true) ?: return null
  val checker = JsonSchemaAnnotatorChecker(project, JsonComplianceCheckerOptions(false, false, true))
  val valueAdapter = psiWalker.createValueAdapter(objectNode) ?: return null
  checker.checkObjectBySchemaRecordErrors(schemaObjectFile, valueAdapter, position)

  val flattenedErrors =
    checker.errors.values.asSequence()
      .filter {
        if (onlyRequired)
          it.fixableIssueKind == FixableIssueKind.MissingProperty
        else
          it.fixableIssueKind == FixableIssueKind.MissingNotRequiredProperty
      }
      .map(JsonValidationError::getIssueData)
      .flatMap { issueData ->
        when (issueData) {
          is JsonValidationError.MissingMultiplePropsIssueData -> issueData.myMissingPropertyIssues.asSequence()
          is JsonValidationError.MissingPropertyIssueData -> sequenceOf(issueData)
          else -> emptySequence()
        }
      }
      .toList()
      .takeIf { it.isNotEmpty() } ?: return null
  return JsonValidationError.MissingMultiplePropsIssueData(flattenedErrors)
}
