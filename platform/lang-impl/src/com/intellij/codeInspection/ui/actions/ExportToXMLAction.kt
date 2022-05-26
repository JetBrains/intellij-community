// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.ui.actions

import com.intellij.codeInspection.InspectionsBundle
import com.intellij.codeInspection.InspectionsResultUtil
import com.intellij.codeInspection.ex.InspectionProfileImpl
import com.intellij.codeInspection.ex.InspectionToolWrapper
import com.intellij.codeInspection.ex.ScopeToolState
import com.intellij.codeInspection.ui.InspectionNode
import com.intellij.codeInspection.ui.InspectionResultsView
import com.intellij.codeInspection.ui.InspectionTreeModel
import com.intellij.icons.AllIcons
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.containers.MultiMap
import java.io.IOException
import java.nio.file.Path

class ExportToXMLAction : InspectionResultsExportActionBase(InspectionsBundle.messagePointer("inspection.action.export.xml.title"),
                                                            InspectionsBundle.messagePointer("inspection.action.export.xml.description"),
                                                            AllIcons.FileTypes.Xml) {
  override val progressTitle: String = InspectionsBundle.message("inspection.generating.xml.progress.title")

  override fun writeResults(view: InspectionResultsView, outputPath: Path) {
    dumpToXml(outputPath, view)
  }

  companion object {
    fun dumpToXml(outputPath: Path, view: InspectionResultsView) {
      val profile: InspectionProfileImpl = view.currentProfile
      val singleTool = profile.singleTool
      val shortName2Wrapper = MultiMap<String, InspectionToolWrapper<*, *>>()
      if (singleTool != null) {
        shortName2Wrapper.put(singleTool, getWrappersForAllScopes(singleTool, view))
      }
      else {
        val model: InspectionTreeModel = view.tree.inspectionTreeModel
        model
          .traverse(model.root)
          .filter(InspectionNode::class.java)
          .filter { !it.isExcluded }
          .map { obj: InspectionNode -> obj.toolWrapper }
          .forEach { w: InspectionToolWrapper<*, *> -> shortName2Wrapper.putValue(w.shortName, w) }
      }

      for (entry in shortName2Wrapper.entrySet()) {
        val shortName: String = entry.key
        val wrappers: Collection<InspectionToolWrapper<*, *>> = entry.value
        InspectionsResultUtil.writeInspectionResult(view.project, shortName, wrappers, outputPath) {
          wrapper: InspectionToolWrapper<*, *> -> view.globalInspectionContext.getPresentation(wrapper)
        }
      }

      val descriptionsFile: Path = outputPath.resolve(InspectionsResultUtil.DESCRIPTIONS + InspectionsResultUtil.XML_EXTENSION)
      try {
        InspectionsResultUtil.describeInspections(descriptionsFile, profile.name, profile)
      }
      catch (e: javax.xml.stream.XMLStreamException) {
        throw IOException(e)
      }
    }

    private fun getWrappersForAllScopes(shortName: String,
                                        view: InspectionResultsView): Collection<InspectionToolWrapper<*, *>> {
      val context = view.globalInspectionContext
      return when (val tools = context.tools[shortName]) {
        null -> emptyList() //dummy entry points tool
        else -> ContainerUtil.map(tools.tools) { obj: ScopeToolState -> obj.tool }
      }
    }
  }
}