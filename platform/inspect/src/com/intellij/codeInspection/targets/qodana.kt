// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.targets

import com.intellij.analysis.AnalysisScope
import com.intellij.codeInspection.InspectionApplication
import com.intellij.codeInspection.ex.InspectionProfileImpl
import com.intellij.codeInspection.ex.ReportConverterUtil
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.PathMacroManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.util.io.exists
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption

private val LOG = Logger.getInstance(QodanaRunner::class.java)
private const val QODANA_CONFIG_FILENAME = "qodana.json"


fun InspectionApplication.runAnalysisByQodana(path: Path,
                                              project: Project,
                                              baseProfile: InspectionProfileImpl,
                                              scope: AnalysisScope,
                                              disposable: Disposable) {
  QodanaRunner(this, path, project, baseProfile, scope, disposable).run()
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
    val qodanaProfileFile = projectPath.resolve(QODANA_CONFIG_FILENAME)
    val outPath = Paths.get(application.myOutPath)
    if (qodanaProfileFile.exists()) {
      Files.copy(qodanaProfileFile, outPath.resolve(QODANA_CONFIG_FILENAME), StandardCopyOption.REPLACE_EXISTING)
    }
    val qodanaConfig = QodanaConfig.read(qodanaProfileFile)
    val globalScope = qodanaConfig.getGlobalScope(project, scope)
    application.configureProject(projectPath, project, globalScope)
    qodanaConfig.updateScope(baseProfile, project)
    converter.projectData(project, outPath.resolve("projectStructure"))

    application.writeDescriptions(baseProfile, converter)

    application.runAnalysisOnScope(projectPath, disposable, project, baseProfile, globalScope)
  }
}
