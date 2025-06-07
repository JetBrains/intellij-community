package com.intellij.jvm.analysis.internal.testFramework

import com.intellij.codeInspection.OverrideOnlyApiInspection
import com.intellij.jvm.analysis.testFramework.JvmInspectionTestBase
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ContentEntry
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.pom.java.LanguageLevel
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.PsiTestUtil
import com.intellij.util.PathUtil
import org.jetbrains.annotations.ApiStatus

abstract class OverrideOnlyApiInspectionTestBase : JvmInspectionTestBase() {
  override val inspection: OverrideOnlyApiInspection = OverrideOnlyApiInspection()

  override fun getProjectDescriptor(): LightProjectDescriptor = object : ProjectDescriptor(LanguageLevel.HIGHEST) {
    override fun configureModule(module: Module, model: ModifiableRootModel, contentEntry: ContentEntry) {
      super.configureModule(module, model, contentEntry)
      PsiTestUtil.addProjectLibrary(model, "annotations", listOf(PathUtil.getJarPathForClass(ApiStatus.OverrideOnly::class.java)))
      PsiTestUtil.addProjectLibrary(model, "library", listOf(testDataPath))
    }
  }

  override fun getBasePath(): String = "/jvm/jvm-analysis-kotlin-tests-shared/testData/codeInspection/overrideOnly"
}
