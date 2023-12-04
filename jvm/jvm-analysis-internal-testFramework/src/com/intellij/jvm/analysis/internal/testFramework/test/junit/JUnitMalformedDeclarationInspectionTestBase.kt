package com.intellij.jvm.analysis.internal.testFramework.test.junit

import com.intellij.codeInspection.test.junit.JUnitMalformedDeclarationInspection
import com.intellij.jvm.analysis.internal.testFramework.test.addJUnit3Library
import com.intellij.jvm.analysis.internal.testFramework.test.addJUnit4Library
import com.intellij.jvm.analysis.internal.testFramework.test.addJUnit5Library
import com.intellij.jvm.analysis.testFramework.JvmInspectionTestBase
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

abstract class JUnitMalformedDeclarationInspectionTestBase(protected val junit5Version: String = JUNIT5_LATEST) : JvmInspectionTestBase() {
  override val inspection = JUnitMalformedDeclarationInspection()

  protected open class JUnitProjectDescriptor(
    languageLevel: LanguageLevel,
    private val junit5Version: String
  ) : ProjectDescriptor(languageLevel) {
    override fun configureModule(module: Module, model: ModifiableRootModel, contentEntry: ContentEntry) {
      super.configureModule(module, model, contentEntry)
      model.addJUnit3Library()
      model.addJUnit4Library()
      model.addJUnit5Library(junit5Version)
    }
  }

  override fun getProjectDescriptor(): LightProjectDescriptor = JUnitProjectDescriptor(LanguageLevel.HIGHEST, junit5Version)

  protected fun addAutomaticExtension(text: String) {
    val servicesDir = createServiceResourceDir()
    runWriteAction {
      servicesDir.createFile(JUnitCommonClassNames.ORG_JUNIT_JUPITER_API_EXTENSION_EXTENSION).also { file -> file.writeText(text) }
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

  protected companion object {
    const val JUNIT5_7_0 = "5.7.0"
    const val JUNIT5_LATEST = "5.10.0-RC1"
  }
}