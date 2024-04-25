// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.embeddings.utils

import ai.grazie.emb.FloatTextEmbedding
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.platform.ml.embeddings.services.LocalEmbeddingServiceProvider
import kotlin.math.sqrt

// Equivalent to splitting by the following regexp: "(?<=[A-Z])(?=[A-Z][a-z])|(?<=[^A-Z])(?=[A-Z])|(?<=[A-Za-z])(?=[^A-Za-z])"
fun splitIdentifierIntoTokens(identifier: String, lowercase: Boolean = true): String {
  val result = buildString {
    var isPrevUppercase = false
    var isPrevLetter = false
    for (index in identifier.indices) {
      if (
        lastOrNull() != ' ' &&
        ((index < identifier.length - 1 && isPrevUppercase
          && identifier[index].isUpperCase() && (identifier[index + 1].isLetter() && !identifier[index + 1].isUpperCase()))
         || (index > 0 && !isPrevUppercase && identifier[index].isUpperCase())
         || (isPrevLetter && !identifier[index].isLetter()))
      ) {
        append(" ")
      }
      isPrevUppercase = identifier[index].isUpperCase()
      isPrevLetter = identifier[index].isLetter()
      if (identifier[index] != '_' && !(identifier[index] == ' ' && lastOrNull() == ' ')) {
        append(identifier[index])
      }
    }
  }
  return if (lowercase) result.lowercase() else result
}

fun convertNameToNaturalLanguage(pattern: String): String {
  val meaningfulName = if (pattern.contains(".")) {
    pattern.split(".").dropLast(1).joinToString(".")
  }
  else pattern
  return splitIdentifierIntoTokens(meaningfulName)
}

fun generateEmbeddingBlocking(indexableRepresentation: String, downloadArtifacts: Boolean = false): FloatTextEmbedding? {
  return runBlockingMaybeCancellable { generateEmbedding(indexableRepresentation, downloadArtifacts) }
}

suspend fun generateEmbedding(indexableRepresentation: String, downloadArtifacts: Boolean = false): FloatTextEmbedding? {
  return generateEmbeddings(listOf(indexableRepresentation), downloadArtifacts)?.single()
}

suspend fun generateEmbeddings(texts: List<String>, downloadArtifacts: Boolean = false): List<FloatTextEmbedding>? {
  return serviceAsync<LocalEmbeddingServiceProvider>().getService(downloadArtifacts)?.embed(texts)?.map { it.normalized() } ?: return null
}

fun FloatTextEmbedding.normalized(): FloatTextEmbedding {
  val norm = sqrt(this * this)
  return FloatTextEmbedding(this.values.map { it / norm }.toFloatArray())
}
