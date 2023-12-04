// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.embeddings.utils

import ai.grazie.emb.FloatTextEmbedding
import com.intellij.platform.ml.embeddings.services.LocalEmbeddingServiceProvider
import kotlin.math.sqrt

private const val SEPARATOR = "~"

private object SplittingRegExps {
  val wordsEndingLocations: List<Regex> = arrayOf(
    "(?<=[A-Za-z])(?=[A-Z][a-z])", "[^\\w\\s]", "[_\\-]").map { it.toRegex() }

  val boundDigitsLocation: Regex = "(?<=\\D)(?=\\d)|(?<=\\d)(?=\\D)".toRegex()
}

fun splitIdentifierIntoTokens(identifier: String): List<String> {
  var transformedIdentifier = identifier
  for (regex in SplittingRegExps.wordsEndingLocations) {
    transformedIdentifier = transformedIdentifier.replace(regex, SEPARATOR)
  }

  return buildList {
    for (token in transformedIdentifier.split(SEPARATOR)) {
      if (token.isEmpty()) continue
      var isNextCharUpperCase = Character.isUpperCase(token.last())
      var transformedToken = token

      for (index in token.length - 2 downTo 0) {
        val isCurCharUpperCase = Character.isUpperCase(transformedToken[index])
        val isCaseChanging = isNextCharUpperCase xor isCurCharUpperCase
        if (isCaseChanging) {
          val splitPosition = index + if (isNextCharUpperCase) 1 else 0
          transformedToken = transformedToken.substring(0, splitPosition) + SEPARATOR + transformedToken.substring(splitPosition)
          isNextCharUpperCase = isCurCharUpperCase
        }
      }

      transformedToken.split(SEPARATOR)
        .flatMap { it.split(SplittingRegExps.boundDigitsLocation) }
        .filterNot(String::isEmpty)
        .map { it.lowercase() }
        .forEach(this::add)
    }
  }
}

suspend fun generateEmbedding(indexableRepresentation: String, downloadArtifacts: Boolean = false): FloatTextEmbedding? {
  return generateEmbeddings(listOf(indexableRepresentation), downloadArtifacts)?.single()
}

suspend fun generateEmbeddings(texts: List<String>, downloadArtifacts: Boolean = false): List<FloatTextEmbedding>? {
  return LocalEmbeddingServiceProvider.getInstance().getService(downloadArtifacts)?.embed(texts)?.map { it.normalized() } ?: return null
}

fun FloatTextEmbedding.normalized(): FloatTextEmbedding {
  val norm = sqrt(this * this)
  return FloatTextEmbedding(this.values.map { it / norm }.toFloatArray())
}
