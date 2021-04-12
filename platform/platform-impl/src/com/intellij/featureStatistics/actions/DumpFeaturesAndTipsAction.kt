// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.featureStatistics.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.featureStatistics.ProductivityFeaturesRegistry
import com.intellij.ide.util.TipAndTrickBean
import com.intellij.ide.util.TipUIUtil
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.DumbAware
import java.awt.datatransfer.StringSelection
import java.lang.StringBuilder

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
          feature.tipFileName?.let { tipFile ->
            featureTipRow.tipFile = feature.tipFileName
            featureTipRow.tipFileExists = TipUIUtil.getTip(feature) != null
            tips.remove(tipFile)
          }
        }
        rows.add(featureTipRow)
      }
      for (tip in tips.values) {
        rows.add(FeatureTipRow(tipFile = tip.fileName, tipFileExists = TipAndTrickBean.findByFileName(tip.fileName) != null))
      }
      val output = StringBuilder()
      output.appendLine(FeatureTipRow.HEADER)
      for (row in rows.sortedWith(compareBy(nullsLast()) { it.group })) {
        output.appendLine(row.toString())
      }
      CopyPasteManager.getInstance().setContents(StringSelection(output.toString()))
    }
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
}