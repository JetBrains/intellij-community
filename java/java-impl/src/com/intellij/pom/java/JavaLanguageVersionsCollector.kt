// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.pom.java

import com.intellij.execution.wsl.WslPath
import com.intellij.internal.statistic.beans.MetricEvent
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.ProjectUsagesCollector
import com.intellij.openapi.module.LanguageLevelUtil
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.JavaSdk
import com.intellij.openapi.roots.LanguageLevelProjectExtension
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.util.lang.JavaVersion
import org.jetbrains.jps.model.java.JdkVersionDetector
import java.util.*

class JavaLanguageVersionsCollector : ProjectUsagesCollector() {
  private val group = EventLogGroup("java.language", 5)

  private val feature = EventFields.Int("feature")
  private val minor = EventFields.Int("minor")
  private val update = EventFields.Int("update")
  private val ea = EventFields.Boolean("ea")
  private val wsl = EventFields.Boolean("wsl")
  private val vendor = EventFields.String("vendor", JdkVersionDetector.VENDORS)

  private val moduleJdkVersion = group.registerVarargEvent("MODULE_JDK_VERSION",
                                                           feature, minor, update, ea, wsl, vendor)
  private val moduleLanguageLevel = group.registerEvent("MODULE_LANGUAGE_LEVEL",
                                                        EventFields.Int("version"),
                                                        EventFields.Boolean("preview"))

  override fun getGroup(): EventLogGroup {
    return group
  }

  public override fun getMetrics(project: Project): Set<MetricEvent> {
    val sdks = ModuleManager.getInstance(project).modules.mapNotNullTo(HashSet()) {
      ModuleRootManager.getInstance(it).sdk
    }.filter { it.sdkType is JavaSdk }

    data class JdkVersionData(val version: JavaVersion?, val wsl: Boolean, val vendor: String)

    val jdkVersions = sdks.mapTo(HashSet()) { sdk ->
      JdkVersionData(
        JavaVersion.tryParse(sdk.versionString),
        (sdk.homePath?.let { WslPath.isWslUncPath(it) } ?: false),
        JdkVersionDetector.VENDORS.firstOrNull { sdk.versionString?.contains(it) == true } ?: JdkVersionDetector.Variant.Unknown.displayName
      )
    }

    val metrics = HashSet<MetricEvent>()
    jdkVersions.mapTo(metrics) { (version, isWsl, detectedVendor) ->
      moduleJdkVersion.metric(
        feature with (version?.feature ?: -1),
        minor with (version?.minor ?: -1),
        update with (version?.update ?: -1),
        ea with (version?.ea ?: false),
        wsl with isWsl,
        vendor with detectedVendor
      )
    }

    val projectExtension = LanguageLevelProjectExtension.getInstance(project)
    if (projectExtension != null) {
      val projectLanguageLevel = projectExtension.languageLevel
      val languageLevels = ModuleManager.getInstance(project).modules.mapTo(EnumSet.noneOf(LanguageLevel::class.java)) {
        LanguageLevelUtil.getCustomLanguageLevel(it) ?: projectLanguageLevel
      }
      languageLevels.mapTo(metrics) {
        moduleLanguageLevel.metric(it.feature(), it.isPreview)
      }
    }

    return metrics
  }

  override fun requiresReadAccess(): Boolean = true
}