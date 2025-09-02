// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.impl.tree

import com.jetbrains.jsonSchema.extension.adapters.JsonValueAdapter

/**
 * Represents a request to expand a JSON schema node.
 *
 * @property inspectedValueAdapter The JSON value adapter for the inspected instance node.
 * Might be useful for in-schema resolve that must work with the validated instance
 * to choose which branch to resolve to depending on what user had actually typed.
 * @property strictIfElseBranchChoice FALSE to consider all "if-else" branches of the schema no matter what data
 * the instance file has, TRUE to select only one actually valid against the instance file branch.
 * In cases like incomplete code, it is useful to consider all branches independently of the validated instance code,
 * e.g., to display all possible completions
 */
data class JsonSchemaNodeExpansionRequest(
  val inspectedValueAdapter: JsonValueAdapter?,
  val strictIfElseBranchChoice: Boolean
)