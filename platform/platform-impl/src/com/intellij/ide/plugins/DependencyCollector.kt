// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import com.intellij.util.xmlb.annotations.Attribute

interface DependencyCollector {
  val dependencyKind: String

  fun collectDependencies(project: Project): List<String>

  companion object {
    val EP_NAME = ExtensionPointName.create<DependencyCollector>("com.intellij.dependencyCollector")
  }
}

class DependencySupportBean {
  @Attribute("kind")
  @JvmField
  var kind: String = ""

  @Attribute("coordinate")
  @JvmField
  var coordinate: String = ""

  companion object {
    val EP_NAME = ExtensionPointName.create<DependencySupportBean>("com.intellij.dependencySupport")
  }
}

const val DEPENDENCY_SUPPORT_FEATURE = "dependencySupport"

class DependencyFeatureCollector : StartupActivity.Background {
  override fun runActivity(project: Project) {
    PluginFeatureService.instance.collectFeatureMapping(
      DEPENDENCY_SUPPORT_FEATURE,
      DependencySupportBean.EP_NAME,
      { bean -> bean.kind + ":" + bean.coordinate },
      { bean -> bean.kind + ":" + bean.coordinate }
    )
  }
}
