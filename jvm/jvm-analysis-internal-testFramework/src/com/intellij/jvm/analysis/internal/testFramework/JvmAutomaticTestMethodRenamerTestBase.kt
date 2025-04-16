package com.intellij.jvm.analysis.internal.testFramework

import com.intellij.jvm.analysis.testFramework.LightJvmCodeInsightFixtureTestCase
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ContentEntry
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.refactoring.rename.naming.AutomaticRenamerFactory
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor
import com.intellij.util.PathUtil
import org.junit.jupiter.api.Nested
import java.nio.file.Path
import kotlin.io.path.name
import kotlin.io.path.pathString

abstract class JvmAutomaticTestMethodRenamerTestBase : LightJvmCodeInsightFixtureTestCase() {
  override fun getProjectDescriptor(): LightProjectDescriptor {
    return object : DefaultLightProjectDescriptor() {
      override fun configureModule(module: Module, model: ModifiableRootModel, contentEntry: ContentEntry) {
        super.configureModule(module, model, contentEntry)
        val jar1 = Path.of(PathUtil.getJarPathForClass(Nested::class.java))
        PsiTestUtil.addLibrary(model, "JUnit", jar1.parent.pathString, jar1.name)
        contentEntry.addSourceFolder(contentEntry.url + "/${getTestName(true)}/test_src", true)
      }
    }
  }
  protected fun doTest(filename: String) {
    val filePath = "${getTestName(true)}/test_src/$filename"
    myFixture.configureByFile(filePath)
    val element = myFixture.elementAtCaret
    AutomaticRenamerFactory.EP_NAME.extensionList.forEach {
      assertFalse(it.isApplicable(element))
    }
  }
}