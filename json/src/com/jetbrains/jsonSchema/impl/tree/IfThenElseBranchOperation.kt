// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.impl.tree

import com.jetbrains.jsonSchema.extension.JsonLikePsiWalker
import com.jetbrains.jsonSchema.extension.adapters.JsonValueAdapter
import com.jetbrains.jsonSchema.fus.JsonSchemaFusCountedFeature
import com.jetbrains.jsonSchema.fus.JsonSchemaHighlightingSessionStatisticsCollector
import com.jetbrains.jsonSchema.ide.JsonSchemaService
import com.jetbrains.jsonSchema.impl.JsonSchemaObject
import com.jetbrains.jsonSchema.impl.JsonSchemaResolver
import com.jetbrains.jsonSchema.impl.light.nodes.inheritBaseSchemaIfNeeded

internal class IfThenElseBranchOperation(schemaObject: JsonSchemaObject, expansionRequest: JsonSchemaNodeExpansionRequest?, private val jsonSchemaService: JsonSchemaService) : Operation(schemaObject, expansionRequest) {
  override fun map(visited: MutableSet<JsonSchemaObject>) {
    if (visited.contains(mySourceNode)) return
    JsonSchemaHighlightingSessionStatisticsCollector.getInstance().reportSchemaUsageFeature(JsonSchemaFusCountedFeature.IfElseExpanded);

    val effectiveBranches = computeEffectiveIfThenElseBranches(myExpansionRequest, mySourceNode)
      ?.mapNotNull {
        if (visited.contains(it)) null
        else inheritBaseSchemaIfNeeded(mySourceNode, it)
      }
    if (!effectiveBranches.isNullOrEmpty()) {
      myChildOperations.addAll(effectiveBranches.map { ProcessDefinitionsOperation(it, jsonSchemaService, myExpansionRequest) })
    }
  }

  public override fun reduce() {
    if (!myChildOperations.isEmpty()) {
      for (operation in myChildOperations) {
        myAnyOfGroup.addAll(operation.myAnyOfGroup)
        myOneOfGroup.addAll(operation.myOneOfGroup)
      }
    }
    else {
      // if the parent schema is not empty, but there is no valid branch, don't miss properties declared in the parent schema
      myAnyOfGroup.add(mySourceNode)
    }
  }

  private fun computeEffectiveIfThenElseBranches(expansionRequest: JsonSchemaNodeExpansionRequest, parent: JsonSchemaObject): List<JsonSchemaObject>? {
    val conditionsList = parent.getIfThenElse() ?: return null
    val effectiveElementAdapter = getContainingObjectAdapterOrSelf(expansionRequest.inspectedValueAdapter)

    return if (effectiveElementAdapter == null || !expansionRequest.strictIfElseBranchChoice) {
      conditionsList.asSequence()
        .flatMap { condition -> sequenceOf(condition.then, condition.`else`) }
        .filterNotNull()
        .map { branch -> inheritBaseSchemaIfNeeded(parent, branch) }
        .toList()
    }
    else {
      conditionsList.asSequence()
        .mapNotNull { condition ->
          if (JsonSchemaResolver.isCorrect(effectiveElementAdapter, condition.`if`)) {
            condition.then
          }
          else {
            condition.`else`
          }
        }
        .map { branch -> inheritBaseSchemaIfNeeded(parent, branch) }
        .toList()
    }
  }

  private fun getContainingObjectAdapterOrSelf(inspectedValueAdapter: JsonValueAdapter?): JsonValueAdapter? {
    val myInspectedRootElementAdapter: JsonValueAdapter?
    if (inspectedValueAdapter != null && inspectedValueAdapter.isObject) {
      myInspectedRootElementAdapter = inspectedValueAdapter
    }
    else {
      val inspectedValuePsi = inspectedValueAdapter?.delegate
      val psiWalker = if (inspectedValuePsi == null) null else JsonLikePsiWalker.getWalker(inspectedValuePsi)
      val parentPropertyAdapter = psiWalker?.getParentPropertyAdapter(inspectedValuePsi!!)
      val parentObjectAdapter = parentPropertyAdapter?.parentObject
      myInspectedRootElementAdapter = parentObjectAdapter ?: inspectedValueAdapter
    }
    return myInspectedRootElementAdapter
  }
}