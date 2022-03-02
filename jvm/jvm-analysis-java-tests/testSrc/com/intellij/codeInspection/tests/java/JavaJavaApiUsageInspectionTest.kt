package com.intellij.codeInspection.tests.java

import com.intellij.codeInspection.tests.JavaApiUsageInspectionTestBase
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
class JavaJavaApiUsageInspectionTest : JavaApiUsageInspectionTestBase() {
  override fun getBasePath(): String = JavaJvmAnalysisTestUtil.TEST_DATA_PROJECT_RELATIVE_BASE_PATH

  override fun getProjectDescriptor(): LightProjectDescriptor = object : ProjectDescriptor(languageLevel) {
    override fun configureModule(module: Module, model: ModifiableRootModel, contentEntry: ContentEntry) {
      super.configureModule(module, model, contentEntry)
      val dataDir = "$testDataPath/codeInspection/apiUsage"
      PsiTestUtil.newLibrary("JDKMock").classesRoot("$dataDir/classes").addTo(model)
    }
  }

  fun `test constructor`() {
    myFixture.setLanguageLevel(LanguageLevel.JDK_1_4)
    myFixture.testHighlighting(ULanguage.JAVA, """
      class Constructor {
        void foo() {
          throw new <error descr="Usage of API documented as @since 1.5+">IllegalArgumentException</error>("", new RuntimeException());
        }
      }
    """.trimIndent())
  }

  fun `test ignored`() {
    myFixture.setLanguageLevel(LanguageLevel.JDK_1_6)
    myFixture.addClass("""
      package java.awt.geom; 
      
      public class GeneralPath {
        public void moveTo(int x, int y) { }
      }
    """.trimIndent())
    myFixture.testHighlighting(ULanguage.JAVA, """
      import java.awt.geom.GeneralPath;
      
      class Ignored {
        void foo() {
          GeneralPath path = new GeneralPath();
          path.moveTo(0,0);
        }
      }
    """.trimIndent())
  }

  fun `test qualified reference`() {
    myFixture.setLanguageLevel(LanguageLevel.JDK_1_6)
    myFixture.testHighlighting(ULanguage.JAVA, """
      import java.nio.charset.StandardCharsets;
      import java.nio.charset.Charset;
      
      class Main {
        void foo() {
          Charset utf = <error descr="Usage of API documented as @since 1.7+">StandardCharsets</error>.UTF_8;
        }
      }
    """.trimIndent())
  }

  fun `test annotation`() {
    myFixture.setLanguageLevel(LanguageLevel.JDK_1_6)
    myFixture.testHighlighting(ULanguage.JAVA, """
      class Annotation {
        @<error descr="Usage of API documented as @since 1.7+">SafeVarargs</error>
        public final void a(java.util.List<String>... ls) {}
      }
    """.trimIndent())
  }

  fun `test override annotation`() {
    myFixture.setLanguageLevel(LanguageLevel.JDK_1_6)
    myFixture.testHighlighting(ULanguage.JAVA, """
      import java.util.Map;

      abstract class OverrideAnnotation implements Map<String, String> {
        @<error descr="Usage of API documented as @since 1.8+">Override</error>
        public String getOrDefault(Object key, String defaultValue) {
          return null;
        }
      }
    """.trimIndent())
  }

  fun `test default methods`() {
    myFixture.setLanguageLevel(LanguageLevel.JDK_1_6)
    myFixture.testHighlighting(ULanguage.JAVA, """
      import java.util.Iterator;
      
      class <error descr="Default method 'remove' is not overridden. It would cause compilation problems with JDK 6">DefaultMethods</error> implements Iterator<String> {
        @Override
        public boolean hasNext() {
          return false;
        }

        @Override
        public String next() {
          return null;
        }

        static class T implements Iterator<String> {
          @Override
          public boolean hasNext() {
            return false;
          }

          @Override
          public String next() {
            return null;
          }

          @Override
          public void remove() {}
        }

        {
          Iterator<String> it = new <error descr="Default method 'remove' is not overridden. It would cause compilation problems with JDK 6">Iterator</error><String>() {
            @Override
            public boolean hasNext(){
              return false;
            }
            
            @Override
            public String next(){
              return null;
            }
          };
        }
      }
    """.trimIndent())
  }

  fun `test raw inherit from newly generified`() {
    myFixture.setLanguageLevel(LanguageLevel.JDK_1_6)
    myFixture.addClass("""
      package javax.swing;
      
      public class AbstractListModel<K> {}
    """.trimIndent())
    myFixture.testHighlighting(ULanguage.JAVA, """
      class RawInheritFromNewlyGenerified {
        private AbstractCCM<String> myModel;
      }      
      
      abstract class AbstractCCM<T> extends javax.swing.AbstractListModel { }
    """.trimIndent())
  }

  fun `test generified`() {
    myFixture.setLanguageLevel(LanguageLevel.JDK_1_6)
    myFixture.addClass("""
      package javax.swing;
      
      public interface ListModel<E> { }
    """.trimIndent())
    myFixture.addClass("""
      package javax.swing;
      
      public class AbstractListModel<K> implements ListModel<E> { }
    """.trimIndent())
    myFixture.testHighlighting(ULanguage.JAVA, """
      import javax.swing.AbstractListModel;
      
      abstract class AbstractCCM<T> extends <error descr="Usage of generified after 1.6 API which would cause compilation problems with JDK 6">AbstractListModel<T></error> { }
    """.trimIndent())
  }

  fun `test language level 14 with JDK 15`() {
    myFixture.setLanguageLevel(LanguageLevel.JDK_14)
    myFixture.testHighlighting(ULanguage.JAVA, """
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
    myFixture.testHighlighting(ULanguage.JAVA, """
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
    myFixture.testHighlighting(ULanguage.JAVA, """
      class Main {
        {
          String.class.isRecord();
          String.class.<error descr="Usage of API documented as @since 17+">isSealed</error>();
        }
      }
    """.trimIndent())
  }
}