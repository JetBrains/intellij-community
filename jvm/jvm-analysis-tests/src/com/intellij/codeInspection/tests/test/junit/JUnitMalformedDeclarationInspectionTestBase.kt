package com.intellij.codeInspection.tests.test.junit

import com.intellij.codeInspection.test.junit.JUnitMalformedDeclarationInspection
import com.intellij.codeInspection.tests.JvmInspectionTestBase
import com.intellij.codeInspection.tests.test.addJUnit3Library
import com.intellij.codeInspection.tests.test.addJUnit4Library
import com.intellij.codeInspection.tests.test.addJUnit5Library
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ContentEntry
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.writeText
import com.intellij.pom.java.LanguageLevel
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.utils.vfs.createDirectory
import com.intellij.testFramework.utils.vfs.createFile
import com.siyeh.ig.junit.JUnitCommonClassNames
import org.jetbrains.jps.model.java.JavaResourceRootType

abstract class JUnitMalformedDeclarationInspectionTestBase : JvmInspectionTestBase() {
  override val inspection = JUnitMalformedDeclarationInspection()

  protected open class JUnitProjectDescriptor(languageLevel: LanguageLevel) : ProjectDescriptor(languageLevel) {
    override fun configureModule(module: Module, model: ModifiableRootModel, contentEntry: ContentEntry) {
      super.configureModule(module, model, contentEntry)
      model.addJUnit3Library()
      model.addJUnit4Library()
      model.addJUnit5Library()
    }
  }

  override fun getProjectDescriptor(): LightProjectDescriptor = JUnitProjectDescriptor(sdkLevel)

  protected fun addAutomaticExtension(service: String) {
    val servicesDir = createServiceResourceDir()
    runWriteAction {
      servicesDir.createFile(JUnitCommonClassNames.ORG_JUNIT_JUPITER_API_EXTENSION_EXTENSION).also { file -> file.writeText(service) }
    }
  }

  private fun createServiceResourceDir(): VirtualFile {
    return runWriteAction {
      val resourceRoot = myFixture.tempDirFixture.findOrCreateDir("resources").also { root ->
        PsiTestUtil.addSourceRoot(myFixture.module, root, JavaResourceRootType.RESOURCE)
      }
      val metaInf = resourceRoot.createDirectory("META-INF")
      metaInf.createDirectory("services")
    }
  }
}