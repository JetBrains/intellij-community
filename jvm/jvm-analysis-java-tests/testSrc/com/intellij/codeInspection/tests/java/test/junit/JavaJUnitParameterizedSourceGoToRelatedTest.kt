package com.intellij.codeInspection.tests.kotlin.test.junit

import com.intellij.jvm.analysis.internal.testFramework.test.junit.JUnitParameterizedSourceGoToRelatedTestBase
import com.intellij.jvm.analysis.testFramework.JvmLanguage
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ContentEntry
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.pom.java.LanguageLevel
import com.intellij.psi.PsiMethod
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.PsiTestUtil
import com.intellij.util.PathUtil
import java.io.File

class JavaJUnitParameterizedSourceGoToRelatedTest : JUnitParameterizedSourceGoToRelatedTestBase() {
  override fun getProjectDescriptor(): LightProjectDescriptor = object : JUnitProjectDescriptor(LanguageLevel.HIGHEST) {
    override fun configureModule(module: Module, model: ModifiableRootModel, contentEntry: ContentEntry) {
      super.configureModule(module, model, contentEntry)
      val jar = File(PathUtil.getJarPathForClass(JvmStatic::class.java))
      PsiTestUtil.addLibrary(model, "kotlin-stdlib", jar.parent, jar.name)
    }
  }

  fun `test go to method source with explicit name`() {
    myFixture.testGoToRelatedAction(JvmLanguage.JAVA, """
      class Test {
        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.MethodSource("foo")
        public void a<caret>bc(Integer i) { }
      
        public static List<Integer> foo() {
          return new ArrayList<String>();
        }
      }
    """.trimIndent()) { item ->
      val element = item.element as? PsiMethod
      assertNotNull(element)
      assertEquals("foo", element?.name)
      assertEquals(0, element?.parameters?.size)
    }
  }

  fun `test go to method source without explicit name`() {
    myFixture.testGoToRelatedAction(JvmLanguage.JAVA, """
      class Test {
        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.MethodSource
        public void a<caret>bc(Integer i) { }
      
        public static List<Integer> abc() {
          return new ArrayList<String>();
        }
      }
    """.trimIndent()) { item ->
      val element = item.element as? PsiMethod
      assertNotNull(element)
      assertEquals("abc", element?.name)
      assertEquals(0, element?.parameters?.size)
    }
  }
}