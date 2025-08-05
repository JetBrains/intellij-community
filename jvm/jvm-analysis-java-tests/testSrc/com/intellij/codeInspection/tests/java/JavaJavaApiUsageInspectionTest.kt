package com.intellij.codeInspection.tests.java

import com.intellij.jvm.analysis.internal.testFramework.JavaApiUsageInspectionTestBase
import com.intellij.jvm.analysis.testFramework.JvmLanguage
import com.intellij.openapi.module.LanguageLevelUtil
import com.intellij.pom.java.LanguageLevel

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
      import java.lang.SafeVarargs;
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

  fun `test no highlighting in javadoc`() {
    myFixture.setLanguageLevel(LanguageLevel.JDK_1_7)
    myFixture.testHighlighting(JvmLanguage.JAVA, """
      class Javadoc {
        /**
         * {@link java.util.function.Predicate}
         */
        void test() {
          return;
        }
      }
    """.trimIndent())
  }

  fun `test minimum since no highlighting`() {
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

  fun `test language level 14`() {
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

  fun `test language level 15`() {
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

  fun `test language level 16`() {
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

  fun `test language level 17`() {
    myFixture.setLanguageLevel(LanguageLevel.JDK_17)
    myFixture.testHighlighting(JvmLanguage.JAVA, """
      import java.time.Duration;
      
      class Main {
        {
          try {
            Thread.<error descr="Usage of API documented as @since 19+">sleep</error>(Duration.ofSeconds(5));
          } catch (InterruptedException e) { }
        }
      }
    """.trimIndent())
  }

  fun `test language level quick fix`() {
    myFixture.setLanguageLevel(LanguageLevel.JDK_17)
    myFixture.configureByText("Main.java", """
      import java.time.Duration;
      
      class Main {
        {
          try {
            Thread.sl<caret>eep(Duration.ofSeconds(5));
          } catch (InterruptedException e) { }
        }
      }
    """.trimIndent())
    myFixture.runQuickFix("Set language level to 19 - No new language features")
    assertEquals(LanguageLevel.JDK_19, LanguageLevelUtil.getEffectiveLanguageLevel(myFixture.module))
  }

  fun `test override with different since version`() {
    myFixture.setLanguageLevel(LanguageLevel.JDK_1_8)
    myFixture.testHighlighting(JvmLanguage.JAVA, """
      import java.io.FilterOutputStream;
      import java.io.IOException;
      import java.nio.charset.StandardCharsets;
      
      class Main {
        public static void main(String[] args) throws IOException {
          byte[] buff = "hello\n".getBytes(StandardCharsets.UTF_8);
          System.out.write(buff); // call to PrintStream in JDK 14
          ((FilterOutputStream) System.out).write(buff); // call to FilterOutputStream in JDK below 14
        }
      }
    """.trimIndent())
  }

  fun `test import for static methods`() {
    myFixture.addClass("""
      package java.lang;
      public class IO {
        public static void println() {}
      }
    """.trimIndent())
    myFixture.setLanguageLevel(LanguageLevel.JDK_1_8)
    myFixture.testHighlighting(JvmLanguage.JAVA, """
      import static java.lang.<error descr="Usage of API documented as @since 25+">IO</error>.println;

      class SimpleClass {
          public static void main(String[] args) {
          }
          void foo() {
              String s = "";
              println();
          }
      }
    """.trimIndent())
  }
}