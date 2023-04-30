// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.images

import org.jetbrains.jps.model.module.JpsModule

class IntellijIconClassGeneratorConfig : IconClasses() {
  override val modules: List<JpsModule>
    get() = super.modules.filterNot {
      // TODO: use icon-robots.txt
      it.name.startsWith("fleet")
    }

  override fun getConfigForModule(moduleName: String): IntellijIconClassGeneratorModuleConfig? {
    @Suppress("SpellCheckingInspection")
    return when (moduleName) {
      "intellij.devkit" -> IntellijIconClassGeneratorModuleConfig(
        className = "DevKitIcons",
        packageName = "org.jetbrains.idea.devkit",
      )
      // force generating "Groovy" inner class to preserve backward compatiblity
      "intellij.groovy.psi" -> IntellijIconClassGeneratorModuleConfig(className = "JetgroovyIcons", iconDirectory = "icons")
      "intellij.clouds.docker" -> IntellijIconClassGeneratorModuleConfig(className = "DockerIcons", packageName = "com.intellij.docker")
      "intellij.css" -> IntellijIconClassGeneratorModuleConfig(
        className = "CssIcons",
        packageName = "com.intellij.lang.css",
        iconDirectory = "icons/css",
      )
      "intellij.properties.psi" -> IntellijIconClassGeneratorModuleConfig(
        className = "PropertiesIcons",
        packageName = "com.intellij.lang.properties",
        iconDirectory = "icons",
      )
      "intellij.spring" -> IntellijIconClassGeneratorModuleConfig(
        className = "SpringApiIcons",
        packageName = "com.intellij.spring",
      )

      "intellij.spring.mvc.core" -> IntellijIconClassGeneratorModuleConfig(
        className = "SpringMvcApiIcons",
        packageName = "com.intellij.spring.mvc",
      )

      "intellij.spring.persistence" -> IntellijIconClassGeneratorModuleConfig(
        className = "SpringPersistenceIntegrationIcons",
        packageName = "com.intellij.spring.persistence.integration",
      )

      "intellij.spring.boot" -> IntellijIconClassGeneratorModuleConfig(
        className = "SpringBootApiIcons",
        packageName = "com.intellij.spring.boot",
      )

      // default name 'com.goide.GOIcons' clashes with existing 'com.goide.GoIcons'
      "intellij.go.impl" -> IntellijIconClassGeneratorModuleConfig(className = "GoGeneratedIcons", packageName = "com.goide")
      "intellij.toml.core" -> IntellijIconClassGeneratorModuleConfig(className = "TomlIcons", packageName = "org.toml")
      "intellij.markdown.core" -> IntellijIconClassGeneratorModuleConfig(className = "MarkdownIcons",
                                                                         packageName = "org.intellij.plugins.markdown")

      "intellij.grazie.core" -> IntellijIconClassGeneratorModuleConfig(className = "GrazieIcons", packageName = "com.intellij.grazie.icons")
      "intellij.sh.core" -> IntellijIconClassGeneratorModuleConfig(className = "ShIcons", packageName = "com.intellij.sh")
      "intellij.django.core" -> IntellijIconClassGeneratorModuleConfig(
        className = "DjangoIcons",
        packageName = "com.jetbrains.django"
      )
      "intellij.jinja" -> IntellijIconClassGeneratorModuleConfig(
        className = "Jinja2Icons",
        packageName = "com.jetbrains.jinja2"
      )

      "intellij.bigdatatools.visualisation" -> IntellijIconClassGeneratorModuleConfig(className = "BigdatatoolsVisualisationIcons",
                                                                                      packageName = "com.intellij.bigdatatools.visualization")
      "intellij.bigdatatools.emr" -> IntellijIconClassGeneratorModuleConfig(className = "BigdatatoolsEmrIcons",
                                                                                      packageName = "com.intellij.bigdatatools.emr")
      "intellij.bigdatatools.zeppelin" -> IntellijIconClassGeneratorModuleConfig(className = "BigdatatoolsZeppelinIcons",
                                                                                 packageName = "com.intellij.bigdatatools.zeppelin")
      "intellij.bigdatatools.tencent.cos" -> IntellijIconClassGeneratorModuleConfig(className = "BigdatatoolsTencentCosIcons",
                                                                                    packageName = "com.intellij.bigdatatools.tencent.cos")
      "intellij.bigdatatools.sparkSubmit" -> IntellijIconClassGeneratorModuleConfig(className = "BigdatatoolsSparkSubmitIcons",
                                                                                    packageName = "com.intellij.bigdatatools.sparkSubmit")
      "intellij.bigdatatools.sparkMonitoring" -> IntellijIconClassGeneratorModuleConfig(className = "BigdatatoolsSparkMonitoringIcons",
                                                                                        packageName = "com.intellij.bigdatatools.sparkMonitoring")
      "intellij.bigdatatools.sftp" -> IntellijIconClassGeneratorModuleConfig(className = "BigdatatoolsSftpIcons",
                                                                             packageName = "com.intellij.bigdatatools.sftp")
      "intellij.bigdatatools.kafka" -> IntellijIconClassGeneratorModuleConfig(className = "BigdatatoolsKafkaIcons",
                                                                              packageName = "com.intellij.bigdatatools.kafka")
      "intellij.bigdatatools.jupyter" -> IntellijIconClassGeneratorModuleConfig(className = "BigdatatoolsJupyterIcons",
                                                                                packageName = "com.intellij.bigdatatools.notebooks")
      "intellij.bigdatatools.hiveMetastore" -> IntellijIconClassGeneratorModuleConfig(className = "BigdatatoolsHiveMetastoreIcons",
                                                                                      packageName = "com.intellij.bigdatatools.hiveMetastore")
      "intellij.bigdatatools.hdfs" -> IntellijIconClassGeneratorModuleConfig(className = "BigdatatoolsHdfsIcons",
                                                                             packageName = "com.intellij.bigdatatools.hdfs")
      "intellij.bigdatatools.hadoopMonitoring" -> IntellijIconClassGeneratorModuleConfig(className = "BigdatatoolsHadoopMonitoringIcons",
                                                                                         packageName = "com.intellij.bigdatatools.hadoopMonitoring")
      "intellij.bigdatatools.glue" -> IntellijIconClassGeneratorModuleConfig(className = "BigdatatoolsGlueIcons",
                                                                             packageName = "com.intellij.bigdatatools.glue")
      "intellij.bigdatatools.flink" -> IntellijIconClassGeneratorModuleConfig(className = "BigdatatoolsFlinkIcons",
                                                                              packageName = "com.intellij.bigdatatools.flink")
      "intellij.bigdatatools.dataproc" -> IntellijIconClassGeneratorModuleConfig(className = "BigdatatoolsDataprocIcons",
                                                                                 packageName = "com.intellij.bigdatatools.dataproc")
      "intellij.bigdatatools.databricks" -> IntellijIconClassGeneratorModuleConfig(className = "BigdatatoolsDatabricksIcons",
                                                                                   packageName = "com.intellij.bigdatatools.databricks")
      "intellij.bigdatatools.common" -> IntellijIconClassGeneratorModuleConfig(className = "BigdatatoolsCommonIcons",
                                                                               packageName = "com.intellij.bigdatatools.common")
      "intellij.swagger.core" -> IntellijIconClassGeneratorModuleConfig(className = "SwaggerCoreIcons",
                                                                        packageName = "com.intellij.swagger.core")
      "intellij.ml.llm" -> IntellijIconClassGeneratorModuleConfig(className = "MLLlmIcons", packageName = "com.intellij.ml.llm")
      "intellij.ml.llm.core" -> IntellijIconClassGeneratorModuleConfig(className = "MLLlmCoreIcons", packageName = "com.intellij.ml.llm.core")

      "intellij.dts" -> IntellijIconClassGeneratorModuleConfig(className = "DtsIcons", packageName = "com.intellij.dts")

      else -> super.getConfigForModule(moduleName)
    }
  }
}