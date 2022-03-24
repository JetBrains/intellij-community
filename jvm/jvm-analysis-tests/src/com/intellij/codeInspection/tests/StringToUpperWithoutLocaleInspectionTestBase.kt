package com.intellij.codeInspection.tests

import com.intellij.codeInspection.StringToUpperWithoutLocale2Inspection
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ContentEntry
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.PsiTestUtil
import com.intellij.util.PathUtil
import org.jetbrains.annotations.NonNls
import java.io.File

abstract class StringToUpperWithoutLocaleInspectionTestBase : UastInspectionTestBase() {
  override val inspection = StringToUpperWithoutLocale2Inspection()

  override fun getProjectDescriptor(): LightProjectDescriptor = object : ProjectDescriptor(sdkLevel) {
    override fun configureModule(module: Module, model: ModifiableRootModel, contentEntry: ContentEntry) {
      super.configureModule(module, model, contentEntry)
      val jar = File(PathUtil.getJarPathForClass(NonNls::class.java))
      PsiTestUtil.addLibrary(model, "annotations", jar.parent, jar.name)
    }
  }
}