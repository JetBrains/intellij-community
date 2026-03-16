package com.intellij.codeInspection.tests.java

import com.intellij.jvm.analysis.internal.testFramework.JavaApiUsageInspectionTestBase
import com.intellij.jvm.analysis.testFramework.JvmLanguage
import com.intellij.pom.java.LanguageLevel
import com.intellij.testFramework.LightProjectDescriptor

class JavaJavaApiUsageInspection22Test : JavaApiUsageInspectionTestBase() {
  override fun getProjectDescriptor(): LightProjectDescriptor {
    return JAVA_22
  }
  
  fun addGatherer() {
    myFixture.addClass("""
      package java.util.stream;
      @jdk.internal.javac.PreviewFeature(feature = jdk.internal.javac.PreviewFeature.Feature.STREAM_GATHERERS)
      public final class Gatherers {}
    """.trimIndent())
  }

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