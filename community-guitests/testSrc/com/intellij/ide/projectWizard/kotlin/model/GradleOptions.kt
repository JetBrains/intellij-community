// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.projectWizard.kotlin.model

enum class GradleGroupModules(val title: String) {
  ExplicitModuleGroups("using explicit module groups"),
  QualifiedNames("using qualified names");

  override fun toString() = title
}

enum class GradleOptions(val title: String, val value: String) {
  UseAutoImport("useAutoImport","Use auto-import"),
  GroupModules("groupModules","Group Modules"),
  SeparateModules("useSeparateModules","Create separate module per source set");

  override fun toString() = title
}

data class GradleProjectOptions(private val map: Map<String, Any?>) {
  val useAutoImport: Boolean by map
  val groupModules: GradleGroupModules by map
  val useSeparateModules: Boolean by map
}

class BuildGradleOptions {
  var useAutoImport = false
  var groupModules = GradleGroupModules.ExplicitModuleGroups
  var useSeparateModules = true

  fun build(init: BuildGradleOptions.()->Unit): GradleProjectOptions {
    init()
    return GradleProjectOptions(mapOf(
      GradleOptions.UseAutoImport.title to useAutoImport,
      GradleOptions.GroupModules.title to groupModules,
      GradleOptions.SeparateModules.title to useSeparateModules)
    )
  }

  fun build() = build {  }
}
