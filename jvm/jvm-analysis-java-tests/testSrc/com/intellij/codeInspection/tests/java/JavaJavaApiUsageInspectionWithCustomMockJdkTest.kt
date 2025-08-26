package com.intellij.codeInspection.tests.java

import com.intellij.jvm.analysis.JavaJvmAnalysisTestUtil
import com.intellij.jvm.analysis.internal.testFramework.JavaApiUsageInspectionTestBase
import com.intellij.jvm.analysis.testFramework.JvmLanguage
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ContentEntry
import com.intellij.openapi.roots.LanguageLevelProjectExtension
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.pom.java.LanguageLevel
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.PsiTestUtil

/**
 * This is a base test case for test cases that highlight all the use of APIs
 * that were introduced in later language levels compared to the current language level.
 *
 * To add a new test case:
 * - Go to `community/jvm/jvm-analysis-java-tests/testData/codeInspection/apiUsage`
 * - Add a new file(s) to `./src` that contains the new API. It's better to define the new API as native methods
 * - Set `JAVA_HOME` to JDK 1.8. In this case it's possible to redefine JDK's own classes like `String` or `Class`
 * - Invoke `./compile.sh`. The new class(es) will appear in `./classes`
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

  fun `test language level 24 preview with JDK 25`() {
    myFixture.addClass("""
      package jdk.internal.javac;
      
      import java.lang.annotation.*;
      
      @Target({ElementType.METHOD,
               ElementType.CONSTRUCTOR,
               ElementType.FIELD,
               ElementType.PACKAGE,
               ElementType.TYPE})
       // CLASS retention will hopefully be sufficient for the purposes at hand
      @Retention(RetentionPolicy.RUNTIME)
      // *Not* @Documented
      public @interface PreviewFeature {
          /**
           * Name of the preview feature the annotated API is associated
           * with.
           */
          public Feature feature();
      
          public enum Feature {
            PEM_API,
            STABLE_VALUES
          }
      }
    """.trimIndent())

    myFixture.addClass("""
      package java.lang;
      
      import jdk.internal.javac.PreviewFeature;
      
      /**
       * @since 25
       */
      @PreviewFeature(feature = PreviewFeature.Feature.STABLE_VALUES)
      public class StableValue<T> {
        private StableValue(T value) {}
        public static <X> StableValue<X> of(X value) {
          return new StableValue<>(value);
        }
      }
    """.trimIndent())

    myFixture.setLanguageLevel(LanguageLevel.JDK_24)
    println("DBG: language level is: " + LanguageLevelProjectExtension.getInstance(project).getLanguageLevel())
    println("DBGL: location: " + JavaPsiFacade.getInstance(project).findClass("java.lang.String", GlobalSearchScope.allScope(project))?.containingFile?.virtualFile?.path)
    myFixture.testHighlighting(JvmLanguage.JAVA, """
      import java.util.concurrent.StructuredTaskScope;

        class Main {
          static void main() {
              StructuredTaskScope a; // JEP 505
              <error descr="Usage of preview API documented as @since 25+"><error descr="java.lang.StableValue is a preview API and is disabled by default">StableValue<String></error></error> b = <error descr="Usage of preview API documented as @since 25+"><error descr="java.lang.StableValue is a preview API and is disabled by default">StableValue</error></error>.<caret>of("foo");
          }
      }
    """.trimIndent())

    val intention = myFixture.getAvailableIntention("Set language level to 25 (Preview) - Primitive Types in Patterns, etc.")
    myFixture.launchAction(intention!!)
  }
}
