package com.intellij.codeInspection.tests.java

import com.intellij.codeInspection.tests.JavaApiUsageInspectionTestBase
import com.intellij.codeInspection.tests.JvmLanguage
import com.intellij.pom.java.LanguageLevel

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
  fun `test constructor`() {
    myFixture.setLanguageLevel(LanguageLevel.JDK_1_4)
    myFixture.testHighlighting(JvmLanguage.JAVA, """
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
    myFixture.testHighlighting(JvmLanguage.JAVA, """
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
    myFixture.testHighlighting(JvmLanguage.JAVA, """
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
    myFixture.testHighlighting(JvmLanguage.JAVA, """
      class Annotation {
        @<error descr="Usage of API documented as @since 1.7+">SafeVarargs</error>
        public final void a(java.util.List<String>... ls) {}
      }
    """.trimIndent())
  }

  fun `test override annotation`() {
    myFixture.setLanguageLevel(LanguageLevel.JDK_1_6)
    myFixture.testHighlighting(JvmLanguage.JAVA, """
      import java.util.Map;

      abstract class OverrideAnnotation implements Map<String, String> {
        @<error descr="Usage of API documented as @since 1.8+">Override</error>
        public String getOrDefault(Object key, String defaultValue) {
          return null;
        }
      }
    """.trimIndent())
  }

  fun `test minimum since highlighting`() {
    myFixture.setLanguageLevel(LanguageLevel.JDK_1_7)
    myFixture.testHighlighting(JvmLanguage.JAVA, """
      import java.util.stream.IntStream;

      class MinimumSince {
        void test() {
          "foo".<error descr="Usage of API documented as @since 1.8+">chars</error>();
        }
      }
    """.trimIndent())
  }

  fun `test minimum since no higlighting`() {
    myFixture.setLanguageLevel(LanguageLevel.JDK_1_8)
    myFixture.testHighlighting(JvmLanguage.JAVA, """
      import java.util.stream.IntStream;

      class MinimumSince {
        void test() {
          "foo".chars();
        }
      }
    """.trimIndent())
  }

  fun `test default methods`() {
    myFixture.setLanguageLevel(LanguageLevel.JDK_1_6)
    myFixture.testHighlighting(JvmLanguage.JAVA, """
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
    myFixture.testHighlighting(JvmLanguage.JAVA, """
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
    myFixture.testHighlighting(JvmLanguage.JAVA, """
      import javax.swing.AbstractListModel;
      
      abstract class AbstractCCM<T> extends <error descr="Usage of generified after 1.6 API which would cause compilation problems with JDK 6">AbstractListModel<T></error> { }
    """.trimIndent())
  }
}