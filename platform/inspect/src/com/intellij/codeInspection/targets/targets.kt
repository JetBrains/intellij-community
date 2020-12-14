// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.targets

import com.google.gson.Gson
import com.intellij.analysis.AnalysisScope
import com.intellij.codeInspection.*
import com.intellij.codeInspection.InspectionsResultUtil.describeInspections
import com.intellij.codeInspection.ex.GlobalInspectionContextEx
import com.intellij.codeInspection.ex.GlobalInspectionContextUtil
import com.intellij.codeInspection.ex.InspectionProfileImpl
import com.intellij.codeInspection.ex.ReportConverterUtil
import com.intellij.openapi.components.PathMacroManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.util.ProgressIndicatorBase
import com.intellij.openapi.project.Project
import com.intellij.util.io.exists
import org.jdom.Element
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.util.concurrent.atomic.AtomicInteger

private val LOG = Logger.getInstance(TargetsRunner::class.java)

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
                            val inspections: Set<String>,
                            val threshold: Int = -1) {
  fun isAboveThreshold(count: Int): Boolean {
    return threshold in 0 until count
  }
}

class TargetsRunner(val application: InspectionApplication,
                    val projectPath: Path,
                    val project: Project,
                    val baseProfile: InspectionProfileImpl,
                    val scope: AnalysisScope) {
  val inspectionCounter = mutableMapOf<TargetDefinition, AtomicInteger>()
  var currentTarget: TargetDefinition? = null
  val macroManager = PathMacroManager.getInstance(project)

  fun run() {
    val converter = ReportConverterUtil.getReportConverter(application.myOutputFormat)
    if (converter == null) {
      LOG.error("Can't find converter ${application.myOutputFormat}")
      return
    }
    val targetsFile = Paths.get(application.myTargets)
    val targets = parseTargets(targetsFile)
    if (targets == null) {
      throw IllegalArgumentException("Empty targets file ${application.myTargets}")
    }
    val targetDefinitions = targets.targets + forgottenTarget(targets.targets, baseProfile)

    application.configureProject(projectPath, project, scope)

    val outPath = Paths.get(application.myOutPath)
    converter.projectData(project, outPath.resolve("projectStructure"))

    //for backward compatibility with teamcity plugin
    if ( "sa" == application.myOutputFormat ) {
      writeProjectDescription(outPath.resolve("projectDescription.json"), project)
    }
    application.writeDescriptions(baseProfile, converter)
    Files.copy(targetsFile, outPath.resolve("targets.json"), StandardCopyOption.REPLACE_EXISTING)

    targetDefinitions.forEach { target ->
      if (!executeTarget(target, converter)) {
        LOG.warn("Inspection run was stopped cause target '${target.description}' reached threshold: ${target.threshold}")
        return
      }
    }
  }

  fun executeTarget(target: TargetDefinition, converter: InspectionsReportConverter): Boolean {
    application.reportMessage(1, "Target ${target.id} (${target.description}) started. Threshold ${target.threshold}")
    println("##teamcity[blockOpened name='Target ${target.id}' description='${target.description}']")

    val targetPath = Paths.get(application.myOutPath).resolve(target.id)
    if (!targetPath.exists()) Files.createDirectory(targetPath)
    val context = application.createGlobalInspectionContext(project)
    currentTarget = target
    val counter = AtomicInteger(0)
    inspectionCounter[target] = counter
    val progressIndicator = createProgressIndicator()

    context.problemConsumer = object : AsyncInspectionToolResultWriter(targetPath) {
      override fun consume(element: Element) {
        val count = counter.incrementAndGet()
        if (target.isAboveThreshold(count)) {
          progressIndicator.cancel()
        }
        macroManager.collapsePathsRecursively(element)
        super.consume(element)
      }
    }
    context.setExternalProfile(constructProfile(target, baseProfile))
    val syncResults = launchTarget(targetPath, context, progressIndicator)
    converter.convert(targetPath.toString(), targetPath.toString(), emptyMap(), syncResults.map { it.toFile() })
    currentTarget = null
    println("##teamcity[blockClosed name='Target ${target.id}']")
    application.reportMessage(1, "Target ${target.id} (${target.description}) finished")
    return !target.isAboveThreshold(counter.get())
  }


  private fun launchTarget(resultsPath: Path, context: GlobalInspectionContextEx, progressIndicator: ProgressIndicator): List<Path> {
    if (!GlobalInspectionContextUtil.canRunInspections(project, false) {}) {
      application.gracefulExit()
      return emptyList()
    }
    val inspectionsResults = mutableListOf<Path>()

    val inspectionProcess = {
      try {
        context.launchInspectionsOffline(scope, resultsPath, application.myRunGlobalToolsOnly, inspectionsResults)
      } catch (e: ProcessCanceledException) {
        LOG.warn("Inspection run was cancelled")
      }
    }

    ProgressManager.getInstance().runProcess(
      inspectionProcess,
      progressIndicator
    )

    return inspectionsResults
  }


  private fun createProgressIndicator(): ProgressIndicatorBase {
    return object : ProgressIndicatorBase() {
      private var myLastPercent = -1

      override fun setText(text: String?) {
        if (text == null) {
          return
        }
        if (!isIndeterminate && fraction > 0) {
          val percent = (fraction * 100).toInt()
          if (myLastPercent == percent) return
          val prefix = InspectionApplication.getPrefix(text)
          myLastPercent = percent
          val msg = (prefix ?: InspectionsBundle.message("inspection.display.name")) + " " + percent + "%"
          application.reportMessage(2, msg)
          println("##teamcity[buildStatus status='SUCCESS' text='${status(percent)}']\n")
        }
        return
      }

      init {
        text = ""
      }
    }
  }

  fun status(percent: Int): String {
    return inspectionCounter.toList().joinToString { (target, count) ->
      if (target == currentTarget) {
        "Running \"${target.description}\" - (${count.get()} problems) - $percent% done"
      }
      else {
        "\"${target.description}\" - ($count problems)"
      }
    }
  }
}

private fun parseTargets(path: Path): Targets? {
  return Gson().fromJson(path.toFile().readText(), Targets::class.java)
}

fun InspectionApplication.runAnalysisByTargets(path: Path,
                                               project: Project,
                                               baseProfile: InspectionProfileImpl,
                                               scope: AnalysisScope) {
  reportMessage(1, InspectionsBundle.message("inspection.application.chosen.profile.log.message", baseProfile.name))

  TargetsRunner(this, path, project, baseProfile, scope).run()
}

private fun InspectionApplication.writeDescriptions(baseProfile: InspectionProfileImpl, converter: InspectionsReportConverter) {
  val descriptionsFile: Path = Paths.get(myOutPath).resolve(InspectionsResultUtil.DESCRIPTIONS + InspectionsResultUtil.XML_EXTENSION)
  val profileName = if (myRunWithEditorSettings) null else baseProfile.name
  describeInspections(descriptionsFile, profileName, baseProfile)
  converter.convert(myOutPath, myOutPath, emptyMap(), listOf(descriptionsFile.toFile()))
}


private fun forgottenTarget(targetDefinitions: List<TargetDefinition>, baseProfile: InspectionProfileImpl): TargetDefinition {
  val usedInspections = targetDefinitions.flatMap { it.inspections }.toSet()
  val forgotten = baseProfile.allTools.map { it.tool.id }.toSet() - usedInspections
  return TargetDefinition("Without targets", "All other inspections", forgotten, -1)
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
