// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.ui.actions

import com.intellij.codeInspection.InspectionsBundle
import com.intellij.codeInspection.ProblemDescriptorBase
import com.intellij.codeInspection.ex.GlobalInspectionContextImpl
import com.intellij.codeInspection.ex.InspectionProfileImpl
import com.intellij.codeInspection.ex.InspectionToolWrapper
import com.intellij.codeInspection.ui.InspectionNode
import com.intellij.codeInspection.ui.InspectionTree
import com.intellij.icons.AllIcons
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.application.ex.ApplicationInfoEx
import com.intellij.openapi.project.Project
import com.jetbrains.qodana.sarif.SarifUtil.writeReport
import com.jetbrains.qodana.sarif.model.*
import java.io.File
import java.net.URI
import java.nio.file.Path
import java.text.SimpleDateFormat
import java.util.*
import java.util.function.Supplier

@Suppress("ComponentNotRegistered")
class ExportToSarifAction : InspectionResultsExportActionProvider(Supplier { "Sarif" },
                                                                  InspectionsBundle.messagePointer("inspection.action.export.sarif.description"),
                                                                  AllIcons.FileTypes.Json) {
  override val progressTitle: String = InspectionsBundle.message("inspection.generating.sarif.progress.title")

  override fun writeResults(tree: InspectionTree,
                            profile: InspectionProfileImpl,
                            globalInspectionContext: GlobalInspectionContextImpl,
                            project: Project,
                            outputPath: Path) {
    val file = File(outputPath.toFile(), "report_${SimpleDateFormat("yyyy-MM-dd_hh-mm-ss").format(Date())}.sarif.json")
    writeReport(file.toPath(), createSarifReport(tree, profile, globalInspectionContext))
  }

  companion object {
    fun createSarifReport(tree: InspectionTree, profile: InspectionProfileImpl, globalInspectionContext: GlobalInspectionContextImpl): SarifReport {
      val appInfo = ApplicationInfoEx.getInstanceEx()

      // Tool (current IDE)
      val run = Run()
        .withTool(
          Tool().withDriver(
            ToolComponent()
              .withName(appInfo.versionName)
              .withInformationUri(URI(appInfo.companyURL))
              .withVersion(appInfo.build.asStringWithoutProductCode())
              .withRules(mutableListOf())
          ))
        .withResults(mutableListOf())

      val addedRules = hashSetOf<String>()

      val addResults = { tool: InspectionToolWrapper<*, *> ->
        globalInspectionContext
          .getPresentation(tool)
          .problemDescriptors
          .map { descriptor ->
            if (!addedRules.contains(tool.id)) {
              // Rule (inspection)
              run.tool.driver.rules.add(
                ReportingDescriptor()
                  .withName(tool.displayName)
                  .withId(tool.id)
              )
              addedRules.add(tool.id)
            }

            // Result (inspection result)
            val result = Result()
              .withMessage(Message().withText(descriptor.toString()))
              .withLevel(tool.defaultLevel.severity.toLevel())
              .withRuleId(tool.id)

            if (descriptor is ProblemDescriptorBase) {
              descriptor.psiElement?.let {
                // Location (file & PSI element location)
                result.locations = listOf(
                  Location().withPhysicalLocation(
                    PhysicalLocation()
                      .withArtifactLocation(
                        ArtifactLocation().withUri(it.containingFile.virtualFile.url)
                      )
                      .withRegion(
                        Region()
                          .withCharOffset(it.textOffset)
                          .withCharLength(it.textLength)
                          .withSnippet(ArtifactContent().withText(it.text))
                      )
                  )
                )
              }
            }

            run.results.add(result)
          }
      }

      if (profile.singleTool != null) {
        globalInspectionContext.tools[profile.singleTool]?.let {
          addResults(it.tool)
        }
      } else {
        tree.inspectionTreeModel
          .traverse(tree.inspectionTreeModel.root)
          .filter(InspectionNode::class.java)
          .forEach { node ->
            addResults(node.toolWrapper)
          }
      }

      val schema = URI("https://raw.githubusercontent.com/schemastore/schemastore/master/src/schemas/json/sarif-2.1.0-rtm.5.json")
      return SarifReport(SarifReport.Version._2_1_0, listOf(run)).`with$schema`(schema)
    }

    private fun HighlightSeverity.toLevel(): Level {
      return when (this) {
        HighlightSeverity.ERROR -> Level.ERROR
        HighlightSeverity.WARNING -> Level.WARNING
        HighlightSeverity.WEAK_WARNING -> Level.NOTE
        else -> Level.NOTE
      }
    }
  }
}