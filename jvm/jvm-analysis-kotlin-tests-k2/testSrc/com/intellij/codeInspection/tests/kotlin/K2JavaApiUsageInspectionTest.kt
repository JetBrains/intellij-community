package com.intellij.codeInspection.tests.kotlin

import com.intellij.jvm.analysis.testFramework.JvmLanguage
import com.intellij.pom.java.LanguageLevel
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode

class K2JavaApiUsageInspectionTest : KotlinJavaApiUsageInspectionTest() {
  override val pluginMode: KotlinPluginMode get() = KotlinPluginMode.K2

  fun `test method that will be overridden in a future Java version`() {
    myFixture.setLanguageLevel(LanguageLevel.JDK_1_7)

    // The reversed method declaration is erroneous because there is a missing `override`
    // In Java, such code would be fine because overrides are implicit
    myFixture.testHighlighting(JvmLanguage.KOTLIN, """
      import java.util.Comparator
      
      
      class MyComparator : Comparator<String> {
          override fun compare(p0: String, p1: String): Int {
              return 0
          }
      
          fun <error descr="Usage of API documented as @since 1.8+"><error descr="[VIRTUAL_MEMBER_HIDDEN] 'reversed' hides member of supertype 'Comparator' and needs an 'override' modifier.">reversed</error></error>(): Comparator<String> {
              return this
          }
      }
    """)
  }
}