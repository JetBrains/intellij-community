package com.intellij.codeInspection.tests.java

import com.intellij.jvm.analysis.internal.testFramework.javaApiUsageInspectionFixture
import com.intellij.jvm.analysis.testFramework.JvmLanguage
import com.intellij.pom.java.LanguageLevel
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Test

@TestApplication
class JavaJavaApiUsageInspection22Test {
  companion object {
    private val projectFixture = lightJavaProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_22)
  }

  private val myFixture by javaApiUsageInspectionFixture(projectFixture)

  
  fun addGatherer() {
    myFixture.addClass("""
      package java.util.stream;
      @jdk.internal.javac.PreviewFeature(feature = jdk.internal.javac.PreviewFeature.Feature.STREAM_GATHERERS)
      public final class Gatherers {}
    """.trimIndent())
  }

  @Test
  fun `test gatherer language level 22 with JDK 22`() {
    myFixture.setLanguageLevel(LanguageLevel.JDK_22)
    addGatherer()
    myFixture.testHighlighting(JvmLanguage.JAVA, """
      import <error descr="java.util.stream.Gatherers is a preview API and is disabled by default">java.util.stream.Gatherers</error>;

      class Main {
          public static void main(String[] args) {
              <error descr="java.util.stream.Gatherers is a preview API and is disabled by default">Gatherers</error> gatherers = null;
          }
      }
    """.trimIndent())
  }

  @Test
  fun `test gatherer language level 22 preview with JDK 22`() {
    myFixture.setLanguageLevel(LanguageLevel.JDK_22_PREVIEW)
    addGatherer()
    myFixture.testHighlighting(JvmLanguage.JAVA, """
      import java.util.stream.Gatherers;
      
      class Main {
          public static void main(String[] args) {
              Gatherers gatherers = null;
          }
      }
    """.trimIndent())
  }
}