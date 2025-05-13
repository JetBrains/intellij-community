package com.intellij.jvm.analysis.internal.testFramework

import com.intellij.codeInspection.MissingDeprecatedAnnotationOnScheduledForRemovalApiInspection
import com.intellij.jvm.analysis.testFramework.JvmInspectionTestBase
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ContentEntry
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.pom.java.LanguageLevel
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.PsiTestUtil
import com.intellij.util.PathUtil
import org.jetbrains.annotations.ApiStatus
import java.io.File

abstract class MissingDeprecatedAnnotationOnScheduledForRemovalApiInspectionTestBase : JvmInspectionTestBase() {
  override val inspection: MissingDeprecatedAnnotationOnScheduledForRemovalApiInspection = MissingDeprecatedAnnotationOnScheduledForRemovalApiInspection()

  @Suppress("DuplicatedCode")
  override fun getProjectDescriptor(): LightProjectDescriptor = object : ProjectDescriptor(LanguageLevel.HIGHEST) {
    override fun configureModule(module: Module, model: ModifiableRootModel, contentEntry: ContentEntry) {
      super.configureModule(module, model, contentEntry)
      val jar = File(PathUtil.getJarPathForClass(ApiStatus.ScheduledForRemoval::class.java))
      PsiTestUtil.addLibrary(model, "annotations", jar.parent, jar.name)
    }
  }
}
