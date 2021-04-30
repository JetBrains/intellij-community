// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.projectView.actions

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.roots.DependencyScope
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.psi.PsiManager
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.VfsTestUtil
import com.intellij.testFramework.rules.ProjectModelRule
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.jps.model.java.JavaSourceRootType
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test

class ExtractModuleFromPackageActionTest {
  companion object {
    @JvmField
    @ClassRule
    val appRule = ApplicationRule()
  }

  @Rule
  @JvmField
  val projectModel = ProjectModelRule()

  @Test
  fun `extract module`() {
    val main = projectModel.createModule("main")
    val dep1 = projectModel.createModule("dep1")
    val dep2 = projectModel.createModule("dep2")
    val exported = projectModel.createModule("exported")
    ModuleRootModificationUtil.addDependency(main, dep1)
    ModuleRootModificationUtil.addDependency(dep1, exported, DependencyScope.COMPILE, true)
    ModuleRootModificationUtil.addDependency(main, dep2)
    val mainSrc = projectModel.addSourceRoot(main, "src", JavaSourceRootType.SOURCE)
    val dep1Src = projectModel.addSourceRoot(dep1, "src", JavaSourceRootType.SOURCE)
    val exportedSrc = projectModel.addSourceRoot(exported, "src", JavaSourceRootType.SOURCE)
    val mainClass = VfsTestUtil.createFile(mainSrc, "xxx/Main.java", "package xxx;\nclass Main extends dep1.Dep1 { exported.Util u; }")
    VfsTestUtil.createFile(dep1Src, "dep1/Dep1.java", "package dep1;\npublic class Dep1 {}")
    VfsTestUtil.createFile(exportedSrc, "exported/Util.java", "package exported;\npublic class Util {}")

    val directory = runReadAction { PsiManager.getInstance(projectModel.project).findDirectory(mainClass.parent)!! }
    ExtractModuleFromPackageAction.extractModuleFromDirectory(directory, main, "main.xxx")
    val xxx = projectModel.moduleManager.findModuleByName("main.xxx")!!
    val xxxRoots = ModuleRootManager.getInstance(xxx)
    assertThat(xxxRoots.sourceRoots).containsExactly(mainClass.parent)
    assertThat(xxxRoots.dependencies).containsExactly(dep1)
  }
}