// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.targets

import com.intellij.analysis.AnalysisScope
import com.intellij.codeInspection.*
import com.intellij.codeInspection.ex.*
import com.intellij.configurationStore.JbXmlOutputter.Companion.collapseMacrosAndWrite
import com.intellij.openapi.components.PathMacroManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.util.ProgressIndicatorBase
import com.intellij.openapi.project.Project
import com.intellij.profile.ProfileEx
import org.jdom.Element
import java.io.Writer
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardOpenOption.*
import java.util.concurrent.atomic.AtomicInteger
import kotlin.system.exitProcess

private val LOG = Logger.getInstance(QodanaRunner::class.java)

class QodanaRunner(val application: InspectionApplication,
                   val projectPath: Path,
                   val project: Project,
                   val baseProfile: InspectionProfileImpl,
                   val scope: AnalysisScope) {
  val inspectionCounter = AtomicInteger()
  val macroManager = PathMacroManager.getInstance(project)
  val config: QodanaConfig = application.myQodanaConfig

  fun run() {
    val converter = ReportConverterUtil.getReportConverter(application.myOutputFormat)
    if (converter == null) {
      LOG.error("Can't find converter ${application.myOutputFormat}")
      return
    }


    val outPath = Paths.get(application.myOutPath)


    application.writeDescriptions(baseProfile, converter)
    writeProfile(baseProfile, outPath, project)

    if (!launch(converter)) {
      application.reportMessage(1,
                                "Inspection run was stopped cause it's reached threshold: ${config.stopThreshold}. Problems count: ${inspectionCounter.get()}")
    }

    if (config.failThreshold in 0 until inspectionCounter.get()) {
      application.reportMessage(1,
                                "Inspection run is terminating with exit code ${DEFAULT_FAIL_EXIT_CODE} cause it's reached fail threshold: ${config.failThreshold}. Problems count: ${inspectionCounter.get()}")
      exitProcess(DEFAULT_FAIL_EXIT_CODE)
    }
  }

  private fun writeProfile(profile: InspectionProfileImpl, outPath: Path, project: Project) {
    profile.initInspectionTools(project)
    val profileElement = Element(ProfileEx.PROFILE)
    profile.writeExternal(profileElement)
    profileElement.setAttribute("version", "1.0")

    val rootElement = Element("component")
      .setAttribute("name", "InspectionProjectProfileManager")
      .addContent(profileElement)

    val path = outPath.resolve("profile.xml")
    val writer: Writer = Files.newBufferedWriter(path, TRUNCATE_EXISTING, CREATE, WRITE)

    writer.use {
      collapseMacrosAndWrite(rootElement, project, writer)
    }
  }

  fun launch(converter: InspectionsReportConverter): Boolean {
    val context = application.createGlobalInspectionContext(project)
    val progressIndicator = createProgressIndicator()

    val outputPath = Paths.get(application.myOutPath)
    val consumer = object : AsyncInspectionToolResultWriter(outputPath) {

      override fun consume(element: Element, toolWrapper: InspectionToolWrapper<*, *>) {
        val count = inspectionCounter.incrementAndGet()
        if (config.isAboveStopThreshold(count)) {
          progressIndicator.cancel()
        }
        macroManager.collapsePathsRecursively(element)
        super.consume(element, toolWrapper)
      }
    }
    context.problemConsumer = consumer
    var afterScope: AnalysisScope?  = null
    if (application.myAnalyzeChanges) {

      val beforeContext = application.createGlobalInspectionContext(project)
      afterScope = application.runAnalysisOnCodeWithoutChanges(
        project,
        beforeContext,
        Runnable {
          val outPathBefore = outputPath.resolve("before")
          outPathBefore.toFile().mkdir()
          launchInspections(scope, outPathBefore, beforeContext, progressIndicator)
        })

      application.setupSecondAnalysisHandler(project, context)
    }

    try {
      val syncResults = launchInspections(afterScope ?: scope, outputPath, context, progressIndicator)
      converter.convert(outputPath.toString(), outputPath.toString(), emptyMap(), syncResults.map { it.toFile() })
    }
    finally {
      consumer.close()
    }

    return !config.isAboveStopThreshold(inspectionCounter.get())
  }


  private fun launchInspections(launchScope: AnalysisScope, resultsPath: Path, context: GlobalInspectionContextEx, progressIndicator: ProgressIndicator): List<Path> {
    application.configureProject(projectPath, project, launchScope)
    if (!GlobalInspectionContextUtil.canRunInspections(project, false) {}) {
      application.gracefulExit()
      return emptyList()
    }
    val converter = ReportConverterUtil.getReportConverter(application.myOutputFormat)

    converter?.projectData(project, resultsPath.resolve("projectStructure"))

    val inspectionsResults = mutableListOf<Path>()

    val inspectionProcess = {
      try {
        context.launchInspectionsOffline(launchScope, resultsPath, application.myRunGlobalToolsOnly, inspectionsResults)
      }
      catch (e: ProcessCanceledException) {
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
        }
        return
      }

      init {
        text = ""
      }
    }
  }
}

fun InspectionApplication.runAnalysisByQodana(path: Path,
                                              project: Project,
                                              baseProfile: InspectionProfileImpl,
                                              scope: AnalysisScope) {
  reportMessage(1, InspectionsBundle.message("inspection.application.chosen.profile.log.message", baseProfile.name))

  QodanaRunner(this, path, project, baseProfile, scope).run()
}

fun InspectionApplication.writeDescriptions(baseProfile: InspectionProfileImpl, converter: InspectionsReportConverter) {
  val descriptionsFile: Path = Paths.get(myOutPath).resolve(InspectionsResultUtil.DESCRIPTIONS + InspectionsResultUtil.XML_EXTENSION)
  val profileName = baseProfile.name
  InspectionsResultUtil.describeInspections(descriptionsFile, profileName, baseProfile)
  converter.convert(myOutPath, myOutPath, emptyMap(), listOf(descriptionsFile.toFile()))
  Files.delete(descriptionsFile)
}