package com.intellij.platform.ml.embeddings.search.indices

interface IndexableEntity {
  val id: String
  val indexableRepresentation: String
}