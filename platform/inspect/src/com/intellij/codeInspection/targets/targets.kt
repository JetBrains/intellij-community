// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.targets

import com.google.gson.Gson
import com.intellij.analysis.AnalysisScope
import com.intellij.codeInspection.*
import com.intellij.codeInspection.InspectionsResultUtil.describeInspections
import com.intellij.codeInspection.ex.GlobalInspectionContextEx
import com.intellij.codeInspection.ex.GlobalInspectionContextUtil
import com.intellij.codeInspection.ex.InspectionProfileImpl
import com.intellij.codeInspection.ex.StaticAnalysisReportConverter
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.util.io.exists
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption

data class Targets(val targets: List<TargetDefinition>,
                   val inspections: List<InspectionMeta>
)

data class InspectionMeta(val id: String,
                          val newSeverity: String,
                          val secondaryTags: List<String>,
                          val locality: String
)

data class TargetDefinition(val id: String,
                            val description: String,
                            val inspections: Set<String>
)

private fun parseTargets(path: Path): Targets? {
  return Gson().fromJson(path.toFile().readText(), Targets::class.java)
}

fun InspectionApplication.runAnalysisByTargets(path: Path,
                                               project: Project,
                                               baseProfile: InspectionProfileImpl,
                                               scope: AnalysisScope) {
  reportMessage(1, InspectionsBundle.message("inspection.application.chosen.profile.log.message", baseProfile.name))

  val targetsFile = Paths.get(myTargets)
  val targets = parseTargets(targetsFile)
  if (targets == null) {
    throw IllegalArgumentException("Empty targets file $path")
  }
  val targetDefinitions = targets.targets + forgottenTarget(targets.targets, baseProfile)

  configureProject(path, project, scope)

  val converter = StaticAnalysisReportConverter()
  converter.projectData(project, myOutPath)
  writeDescriptions(baseProfile, converter)
  Files.copy(targetsFile, Paths.get(myOutPath).resolve("targets.json"), StandardCopyOption.REPLACE_EXISTING)

  targetDefinitions.forEach { target ->
    reportMessage(1, "Target ${target.id} (${target.description}) started")

    val targetPath = Paths.get(myOutPath).resolve(target.id)
    if (!targetPath.exists()) Files.createDirectory(targetPath)
    val context = createGlobalInspectionContext(project)
    context.problemConsumer = AsyncInspectionToolResultWriter(targetPath)
    context.setExternalProfile(constructProfile(target, baseProfile))
    val syncResults = launchTarget(targetPath, context, project, scope)
    converter.convert(targetPath.toString(), targetPath.toString(), emptyMap(), syncResults.map { it.toFile() })

    reportMessage(1, "Target ${target.id} (${target.description}) finished")
  }
}

private fun InspectionApplication.writeDescriptions(baseProfile: InspectionProfileImpl, converter: InspectionsReportConverter) {
  val descriptionsFile: Path = Paths.get(myOutPath).resolve(InspectionsResultUtil.DESCRIPTIONS + InspectionsResultUtil.XML_EXTENSION)
  val profileName = if (myRunWithEditorSettings) null else baseProfile.name
  describeInspections(descriptionsFile, profileName, baseProfile)
  converter.convert(myOutPath, myOutPath, emptyMap(), listOf(descriptionsFile.toFile()))
}

private fun InspectionApplication.launchTarget(resultsPath: Path,
                                       context: GlobalInspectionContextEx,
                                       project: Project,
                                       scope: AnalysisScope): List<Path> {
  if (!GlobalInspectionContextUtil.canRunInspections(project, false) {}) {
    gracefulExit()
    return emptyList()
  }
  val inspectionsResults = mutableListOf<Path>()

  ProgressManager.getInstance().runProcess(
    {
      context.launchInspectionsOffline(scope, resultsPath, myRunGlobalToolsOnly, inspectionsResults)
    },
    createProcessIndicator()
  )

  return inspectionsResults
}

private fun forgottenTarget(targetDefinitions: List<TargetDefinition>, baseProfile: InspectionProfileImpl): TargetDefinition {
  val usedInspections = targetDefinitions.flatMap { it.inspections }.toSet()
  val forgotten = baseProfile.allTools.map { it.tool.id }.toSet() - usedInspections
  return TargetDefinition("Without targets", "All other inspections", forgotten)
}

private fun constructProfile(target: TargetDefinition, baseProfile: InspectionProfileImpl): InspectionProfileImpl {
  val profile = InspectionProfileImpl(target.id)
  profile.copyFrom(baseProfile)

  profile.allTools.forEach { scope ->
    if (scope.tool.id !in target.inspections) {
      scope.isEnabled = false
    }
  }
  return profile
}