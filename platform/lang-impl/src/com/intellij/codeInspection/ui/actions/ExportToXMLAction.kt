// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.ui.actions

import com.intellij.codeInspection.InspectionsBundle
import com.intellij.codeInspection.InspectionsResultUtil
import com.intellij.codeInspection.ex.GlobalInspectionContextImpl
import com.intellij.codeInspection.ex.InspectionProfileImpl
import com.intellij.codeInspection.ex.InspectionToolWrapper
import com.intellij.codeInspection.ex.ScopeToolState
import com.intellij.codeInspection.ui.InspectionNode
import com.intellij.codeInspection.ui.InspectionTree
import com.intellij.codeInspection.ui.InspectionTreeModel
import com.intellij.icons.AllIcons
import com.intellij.openapi.project.Project
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.containers.MultiMap
import java.io.IOException
import java.nio.file.Path
import java.util.function.Supplier

@Suppress("ComponentNotRegistered")
class ExportToXMLAction : InspectionResultsExportActionProvider(Supplier { "XML" },
                                                                InspectionsBundle.messagePointer("inspection.action.export.xml.description"),
                                                                AllIcons.FileTypes.Xml) {
  override val progressTitle: String = InspectionsBundle.message("inspection.generating.xml.progress.title")
  override fun writeResults(tree: InspectionTree,
                            profile: InspectionProfileImpl,
                            globalInspectionContext: GlobalInspectionContextImpl,
                            project: Project,
                            outputPath: Path) {
    dumpToXml(profile, tree, project, globalInspectionContext, outputPath)
  }

  companion object {
    fun dumpToXml(profile: InspectionProfileImpl, tree: InspectionTree, project: Project, globalInspectionContext: GlobalInspectionContextImpl, outputPath: Path) {
      val singleTool = profile.singleTool
      val shortName2Wrapper = MultiMap<String, InspectionToolWrapper<*, *>>()
      if (singleTool != null) {
        shortName2Wrapper.put(singleTool, getWrappersForAllScopes(singleTool, globalInspectionContext))
      }
      else {
        val model: InspectionTreeModel = tree.inspectionTreeModel
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
        InspectionsResultUtil.writeInspectionResult(project, shortName, wrappers, outputPath) {
          wrapper: InspectionToolWrapper<*, *> -> globalInspectionContext.getPresentation(wrapper)
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
                                        context: GlobalInspectionContextImpl): Collection<InspectionToolWrapper<*, *>> {
      return when (val tools = context.tools[shortName]) {
        null -> emptyList() //dummy entry points tool
        else -> ContainerUtil.map(tools.tools) { obj: ScopeToolState -> obj.tool }
      }
    }
  }
}