// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.featureStatistics.actions

import com.intellij.featureStatistics.ProductivityFeaturesRegistry
import com.intellij.ide.util.TipAndTrickBean
import com.intellij.ide.util.TipUIUtil
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.ResourceUtil
import java.awt.datatransfer.StringSelection

class DumpFeaturesAndTipsAction : AnAction(), DumbAware {
  override fun actionPerformed(e: AnActionEvent) {
    ProductivityFeaturesRegistry.getInstance()?.let { featuresRegistry ->
      val tips = TipAndTrickBean.EP_NAME.extensionList.associateBy { it.fileName }.toMutableMap()
      val rows = mutableListOf<FeatureTipRow>()
      for (featureId in featuresRegistry.featureIds) {
        val featureTipRow = FeatureTipRow(featureId)
        featuresRegistry.getFeatureDescriptor(featureId)?.let { feature ->
          featureTipRow.name = feature.displayName
          featureTipRow.group = feature.groupId
          TipUIUtil.getTip(feature)?.let { tip ->
            featureTipRow.tipFile = tip.fileName
            featureTipRow.tipFileExists = tipFileExists(tip)
            tips.remove(tip.fileName)
          }
        }
        rows.add(featureTipRow)
      }
      for (tip in tips.values) {
        rows.add(FeatureTipRow(tipFile = tip.fileName, tipFileExists = tipFileExists(tip)))
      }
      val output = StringBuilder()
      output.appendLine(FeatureTipRow.HEADER)
      for (row in rows.sortedWith(compareBy(nullsLast()) { it.group })) {
        output.appendLine(row.toString())
      }
      CopyPasteManager.getInstance().setContents(StringSelection(output.toString()))
    }
  }

  private fun tipFileExists(tip: TipAndTrickBean): Boolean {
    if (FileUtil.exists(tip.fileName)) return true
    val tipLoader = tip.pluginDescriptor?.pluginClassLoader ?: DumpFeaturesAndTipsAction::class.java.classLoader
    return ResourceUtil.getResourceAsStream(tipLoader, "/tips/", tip.fileName) != null
  }

  private data class FeatureTipRow(var id: String? = null,
                                   var name: String? = null,
                                   var group: String? = null,
                                   var tipFile: String? = null,
                                   var tipFileExists: Boolean = false) {
    companion object {
      private const val EMPTY_VALUE = "NONE"
      private const val FILE_NOT_FOUND = "FILE NOT FOUND"

      const val HEADER = "Feature ID;Name;Group ID;Tip File;Tip File Problem"
    }

    override fun toString(): String =
      "${handleEmpty(id)};${handleEmpty(name)};${handleEmpty(group)};${handleEmpty(tipFile)};${tipFileProblem()}"

    private fun handleEmpty(value: String?) = value ?: EMPTY_VALUE

    private fun tipFileProblem() = if (tipFileExists) "" else FILE_NOT_FOUND
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }
}