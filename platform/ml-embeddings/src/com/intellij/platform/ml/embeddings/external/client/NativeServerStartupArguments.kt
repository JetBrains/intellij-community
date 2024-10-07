// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.embeddings.external.client

import java.nio.file.Path
import kotlin.io.path.absolute

enum class EmbeddingDistanceMetric(val label: String) {
  INNER_PRODUCT("ip"),
  COSINE("cos"),
  SQUARED_EUCLIDEAN("l2sq");

  override fun toString(): String = label
}

enum class EmbeddingQuantization(val label: String) {
  FP32("f32"),
  FP16("f16"),
  INT8("i8"),
  BINARY("b1x8");

  override fun toString(): String = label
}

data class NativeServerStartupArguments(
  val modelPath: Path,
  val vocabPath: Path,
  val storageRoot: Path,
  val vectorLength: Int = 128,
  val indexSizeLimit: Int = 1_000_000,
  val batchSize: Int = 128,
  val modelsPoolSize: Int = 1,
  val maxSequenceLength: Int = 64,
  val quantization: EmbeddingQuantization = EmbeddingQuantization.INT8,
  val metric: EmbeddingDistanceMetric = EmbeddingDistanceMetric.COSINE,
  val intraOpThreadsCount: Int = 2,
  val insertThreadsLimit: Int = 4,
  val batchInsertionThreads: Int = 4,
  val port: Int = 0,
) {
  fun combine(): List<String> = listOf(
    "--model-path", modelPath.absolute().toString(),
    "--vocab-path", vocabPath.absolute().toString(),
    "--storage-root", storageRoot.absolute().toString(),
    "--vector-length", vectorLength.toString(),
    "--index-size-limit", indexSizeLimit.toString(),
    "--batch-size", batchSize.toString(),
    "--models-pool-size", modelsPoolSize.toString(),
    "--max-sequence-length", maxSequenceLength.toString(),
    "--quantization", quantization.toString(),
    "--metric-kind", metric.toString(),
    "--n-threads", intraOpThreadsCount.toString(),
    "--insert-threads-limit", insertThreadsLimit.toString(),
    "--batch-insertion-threads", batchInsertionThreads.toString(),
    "--port", port.toString(),
  )

  override fun toString(): String = combine().joinToString(" ")
}