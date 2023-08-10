// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diagnostic.hprof.analysis

import com.intellij.diagnostic.hprof.util.AnalysisReport
import com.intellij.diagnostic.hprof.util.ListProvider
import com.intellij.ide.plugins.cl.PluginClassLoader
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.progress.ProgressIndicator

class AnalyzeClassloaderReferencesGraph(analysisContext: AnalysisContext, listProvider: ListProvider, val pluginId: String)
  : AnalyzeGraph(analysisContext, listProvider) {
  override fun analyze(progress: ProgressIndicator): AnalysisReport = AnalysisReport().apply {
    traverseInstanceGraph(progress, this)

    val navigator = analysisContext.navigator
    for (l in 1..navigator.instanceCount) {
      val classDefinition = navigator.getClassForObjectId(l)
      if (classDefinition.name == PluginClassLoader::class.java.name) {
        navigator.goTo(l)
        navigator.goToInstanceField(PluginClassLoader::class.java.name, "pluginId")
        navigator.goToInstanceField(PluginId::class.java.name, "idString")
        if (navigator.getStringInstanceFieldValue() == pluginId) {
          val objectId = analysisContext.parentList[l.toInt()]
          if (objectId == 0) {
            return@apply
          }

          val gcRootPathsTree = GCRootPathsTree(analysisContext, AnalysisConfig.TreeDisplayOptions.all(showSize = false), null)
          gcRootPathsTree.registerObject(l.toInt())
          mainReport.append(gcRootPathsTree.printTree())
        }
      }
    }
  }
}
