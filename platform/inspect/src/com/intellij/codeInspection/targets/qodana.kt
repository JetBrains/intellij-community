// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.targets

import com.intellij.analysis.AnalysisScope
import com.intellij.codeInspection.InspectionApplication
import com.intellij.codeInspection.InspectionProfile
import com.intellij.codeInspection.ex.InspectionProfileImpl
import com.intellij.codeInspection.ex.ReportConverterUtil
import com.intellij.configurationStore.JbXmlOutputter.Companion.createOutputter
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.PathMacroManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.CharsetToolkit
import com.intellij.psi.search.scope.packageSet.NamedScope
import com.intellij.psi.search.scope.packageSet.NamedScopesHolder
import com.intellij.util.io.exists
import org.jdom.Element
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

private val LOG = Logger.getInstance(QodanaRunner::class.java)
private val DEFAULT_SCOPES = listOf("All", "Tests", "Production")

fun InspectionApplication.runAnalysisByQodana(path: Path,
                                              project: Project,
                                              baseProfile: InspectionProfileImpl,
                                              scope: AnalysisScope,
                                              disposable: Disposable) {
  //reportMessage(1, InspectionsBundle.message("inspection.application.chosen.profile.log.message", baseProfile.name))

  QodanaRunner(this, path, project, baseProfile, scope, disposable).run()
}

class QodanaProfile(val baseProfile: InspectionProfile, val scopes: List<NamedScope>, ) {

}


class QodanaRunner(val application: InspectionApplication,
                   val projectPath: Path,
                   val project: Project,
                   val baseProfile: InspectionProfileImpl,
                   val scope: AnalysisScope,
                   val disposable: Disposable) {
  val macroManager = PathMacroManager.getInstance(project)

  fun run() {
    val converter = ReportConverterUtil.getReportConverter(application.myOutputFormat)
    if (converter == null) {
      LOG.error("Can't find converter ${application.myOutputFormat}")
      return
    }
    val qodanaProfileFile = projectPath.resolve("qodana.xml")
    if (qodanaProfileFile.exists()) {


    }


    application.configureProject(projectPath, project, scope)

    val outPath = Paths.get(application.myOutPath)
    converter.projectData(project, outPath.resolve("projectStructure"))

    application.writeDescriptions(baseProfile, converter)
    writeQodanaProfile(project, outPath, baseProfile)
    val context = application.createGlobalInspectionContext(project)
    application.runAnalysisOnScope(projectPath, disposable, project, baseProfile, scope)
  }
}

private fun writeQodanaProfile(project: Project, outputPath: Path, baseProfile: InspectionProfileImpl) {
  val root = Element("qodana_profile")

  root.addContent(writeScopes(project, baseProfile))
  val profileElement = Element("base_profile")
  baseProfile.writeExternal(profileElement)
  root.addContent(profileElement)

  val outputter = createOutputter(project)
  val qodanaXmlPath = outputPath.resolve("qodana.xml")
  try {
    Files.newBufferedWriter(qodanaXmlPath, CharsetToolkit.UTF8_CHARSET).use { writer ->
      outputter.output(root, writer)
    }
  }
  catch (e: IOException) {
    LOG.error("Writing qodana.xml error. Path: $qodanaXmlPath", e)
  }
}

private fun writeScopes(project: Project, baseProfile: InspectionProfileImpl): Element {
  val scopesElement = Element("scopes")
  val scopes = baseProfile.allTools.mapNotNull { it.getScope(project) }.distinct().filter { !DEFAULT_SCOPES.contains(it.scopeId) }
  scopes.forEach {
    scopesElement.addContent(NamedScopesHolder.writeScope(it))
  }
  return scopesElement
}