package com.intellij.codeInspection.tests.java

import com.intellij.codeInspection.tests.JavaApiUsageInspectionTestBase
import com.intellij.codeInspection.tests.JvmLanguage
import com.intellij.jvm.analysis.JavaJvmAnalysisTestUtil
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ContentEntry
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.pom.java.LanguageLevel
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.PsiTestUtil

/**
 * This is a base test case for test cases that highlight all the use of API
 * that were introduced in later language levels comparing to the current language level
 *
 * In order to add a new test case:
 * <ol>
 * <li>Go to "community/jvm/jvm-analysis-java-tests/testData/codeInspection/apiUsage"</li>
 * <li>Add a new file(s) to "./src" that contains new API. It's better to define the new API as native methods.</li>
 * <li>Set <code>JAVA_HOME</code> to jdk 1.8. In this case it's possible to redefine JDK's own classes like <code>String</code> or <code>Class</code></li>
 * <li>Invoke "./compile.sh". The new class(es) will appear in "./classes"</li>
 * </ol>
 */
class JavaJavaApiUsageInspectionWithCustomMockJdkTest : JavaApiUsageInspectionTestBase() {
  override fun getBasePath(): String = JavaJvmAnalysisTestUtil.TEST_DATA_PROJECT_RELATIVE_BASE_PATH

  override fun getProjectDescriptor(): LightProjectDescriptor = object : ProjectDescriptor(sdkLevel) {
    override fun configureModule(module: Module, model: ModifiableRootModel, contentEntry: ContentEntry) {
      super.configureModule(module, model, contentEntry)
      val dataDir = "$testDataPath/codeInspection/apiUsage"
      PsiTestUtil.newLibrary("JDKMock").classesRoot("$dataDir/classes").addTo(model)
    }
  }

  fun `test language level 14 with JDK 15`() {
    myFixture.setLanguageLevel(LanguageLevel.JDK_14)
    myFixture.testHighlighting(JvmLanguage.JAVA, """
      class Main {
        {
          g("%s".<error descr="Usage of API documented as @since 15+">formatted</error>(1),
            "".<error descr="Usage of API documented as @since 15+">stripIndent</error>(),
            "".<error descr="Usage of API documented as @since 15+">translateEscapes</error>());
        }

        private void g(String formatted, String stripIndent, String translateEscapes) {}
      }
    """.trimIndent())
  }

  fun `test language level 15 with JDK 16`() {
    myFixture.setLanguageLevel(LanguageLevel.JDK_15)
    myFixture.testHighlighting(JvmLanguage.JAVA, """
      class Main {
        {
          String.class.<error descr="Usage of API documented as @since 16+">isRecord</error>();
          Class.class.<error descr="Usage of API documented as @since 16+">getRecordComponents</error>();
        }
      }
    """.trimIndent())
  }

  fun `test language level 16 with JDK 17`() {
    myFixture.setLanguageLevel(LanguageLevel.JDK_16)
    myFixture.testHighlighting(JvmLanguage.JAVA, """
      class Main {
        {
          String.class.isRecord();
          String.class.<error descr="Usage of API documented as @since 17+">isSealed</error>();
        }
      }
    """.trimIndent())
  }
}