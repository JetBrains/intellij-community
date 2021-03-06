/*
 * Copyright (C) 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.diagnostic.hprof.analysis

class AnalysisConfig(
  val perClassOptions: PerClassOptions,
  val histogramOptions: HistogramOptions = HistogramOptions(),
  val disposerOptions: DisposerOptions = DisposerOptions(),
  val traverseOptions: TraverseOptions = TraverseOptions(),
  val metaInfoOptions: MetaInfoOptions = MetaInfoOptions()
) {

  class PerClassOptions(
    val classNames: List<String>,
    val includeClassList: Boolean = true,
    val treeDisplayOptions: TreeDisplayOptions = TreeDisplayOptions.default()
  )

  class TreeDisplayOptions(
    val minimumObjectSize: Int = 20_000_000,
    val minimumObjectCount: Int = 20_000,
    val minimumSubgraphSize: Long = 100_000_000,
    val minimumObjectCountPercent: Int = 10,
    val maximumTreeDepth: Int = 80,
    val maximumIndent: Int = 40,
    val minimumPaths: Int = 2,
    val headLimit: Int = 100,
    val tailLimit: Int = 25,
    val showFieldNames: Boolean = false,
    val smartIndent: Boolean = true,
    val showSize: Boolean = true
  ) {
    companion object {
      fun default() = TreeDisplayOptions()
      fun all(showFieldNames: Boolean = false,
              smartIndent: Boolean = true,
              showSize: Boolean = true) =
        TreeDisplayOptions(minimumObjectSize = 0,
                           minimumObjectCount = 0,
                           minimumSubgraphSize = 0,
                           minimumObjectCountPercent = 0,
                           headLimit = Int.MAX_VALUE,
                           tailLimit = 0,
                           minimumPaths = Int.MAX_VALUE,
                           showFieldNames = showFieldNames,
                           smartIndent = smartIndent,
                           showSize = showSize)
    }
  }

  class HistogramOptions(
    val includeByCount: Boolean = true,
    val includeBySize: Boolean = true,
    val classByCountLimit: Int = 50,
    val classBySizeLimit: Int = 10
  )

  class DisposerOptions(
    val includeDisposerTree: Boolean = true,
    val includeDisposedObjectsSummary: Boolean = true,
    val includeDisposedObjectsDetails: Boolean = true,
    val disposedObjectsDetailsTreeDisplayOptions: TreeDisplayOptions = TreeDisplayOptions(minimumSubgraphSize = 5_000_000,
                                                                                          headLimit = 70,
                                                                                          tailLimit = 5)
  )

  class TraverseOptions(
    val onlyStrongReferences: Boolean = false,
    val includeClassesAsRoots: Boolean = true
  )

  class MetaInfoOptions(
    val include: Boolean = true
  )

  companion object {
    fun getDefaultConfig(nominatedClasses: List<String>) = AnalysisConfig(PerClassOptions(nominatedClasses))
  }
}
