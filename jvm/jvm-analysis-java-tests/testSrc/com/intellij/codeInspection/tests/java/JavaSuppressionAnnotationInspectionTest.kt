package com.intellij.codeInspection.tests.java

import com.intellij.jvm.analysis.internal.testFramework.SuppressionAnnotationInspectionTestBase
import com.intellij.jvm.analysis.testFramework.JvmLanguage

class JavaSuppressionAnnotationInspectionTest : SuppressionAnnotationInspectionTestBase() {

  fun `test highlighting`() {
    inspection.myAllowedSuppressions.add("FreeSpeech")
    myFixture.testHighlighting(
      JvmLanguage.JAVA,
      """
        <warning descr="Inspection suppression annotation '@SuppressWarnings({\"ALL\", \"SuppressionAnnotation\"})'">@SuppressWarnings({"ALL", "SuppressionAnnotation"})</warning>
        public class A {
          <warning descr="Inspection suppression annotation '@SuppressWarnings(\"PublicField\")'">@SuppressWarnings("PublicField")</warning>
          public String s;
          <warning descr="Inspection suppression annotation '@SuppressWarnings({})'">@SuppressWarnings({})</warning>
          public String t;
    
          void foo() {
            <warning descr="Inspection suppression annotation '//noinspection HardCodedStringLiteral'">//noinspection HardCodedStringLiteral</warning>
            System.out.println("hello");
            <warning descr="Inspection suppression annotation '// noinspection'">// noinspection</warning>
            System.out.println();
          }
        
          @SuppressWarnings("FreeSpeech")
          void bar() {
            //noinspection FreeSpeech
            System.out.println();
          }
        }
        """.trimIndent(),
      fileName = "A"
    )
  }

  fun `test quickfix - remove annotation`() {
    myFixture.testQuickFix(JvmLanguage.JAVA, """
          public class A {
            @SuppressWarnings("PublicField", "Hard<caret>CodedStringLiteral")
            public String s = "test";
          }
        """.trimIndent(), """
          public class A {
            public String s = "test";
          }
        """.trimIndent(), "Remove '@SuppressWarnings' annotation", testPreview = true)
  }

  fun `test quickfix - remove comment`() {
    myFixture.testQuickFix(JvmLanguage.JAVA, """
          public class A {
            //noinspection PublicField, Hard<caret>CodedStringLiteral
            public String s = "test";
          }
        """.trimIndent(), """
          public class A {
            public String s = "test";
          }
        """.trimIndent(), "Remove //noinspection", testPreview = true)
  }

  fun `test quickfix - allow a single suppression from annotation`() {
    testAllowSuppressionQuickFix(JvmLanguage.JAVA, """
          public class A {
            @SuppressWarnings("Public<caret>Field")
            public String s = "test";
          }
        """.trimIndent(), "PublicField")
  }

  fun `test quickfix - allow a single suppression from annotation when array form used`() {
    testAllowSuppressionQuickFix(JvmLanguage.JAVA, """
          public class A {
            @SuppressWarnings({"Public<caret>Field"})
            public String s = "test";
          }
        """.trimIndent(), "PublicField")
  }

  fun `test quickfix - allow a single suppression from annotation when explicit attribute name exists`() {
    testAllowSuppressionQuickFix(JvmLanguage.JAVA, """
          public class A {
            @SuppressWarnings(value = "Public<caret>Field")
            public String s = "test";
          }
        """.trimIndent(), "PublicField")
  }

  fun `test quickfix - allow multiple suppressions from annotation when array form used`() {
    testAllowSuppressionQuickFix(JvmLanguage.JAVA, """
          public class A {
            @SuppressWarnings({"Public<caret>Field", "HardCodedStringLiteral"})
            public String s = "test";
          }
        """.trimIndent(), "PublicField", "HardCodedStringLiteral")
  }

  fun `test quickfix - allow multiple suppressions from annotation when explicit attribute name exists`() {
    testAllowSuppressionQuickFix(JvmLanguage.JAVA, """
          public class A {
            @SuppressWarnings(value = {"Public<caret>Field", "HardCodedStringLiteral"})
            public String s = "test";
          }
        """.trimIndent(), "PublicField", "HardCodedStringLiteral")
  }

  fun `test quickfix - allow multiple suppressions from annotation when constants used`() {
    myFixture.addClass("""
        public final class Constants {
          public static final String PUBLIC_FIELD = "PublicField";
          public static final String HARD_CODED_STRING_LITERAL = "HardCodedStringLiteral";
        }
        """.trimIndent())
    testAllowSuppressionQuickFix(JvmLanguage.JAVA, """
          public class A {
            @Suppress<caret>Warnings({Constants.PUBLIC_FIELD, Constants.HARD_CODED_STRING_LITERAL})
            public String s = "test";
          }
        """.trimIndent(), "PublicField", "HardCodedStringLiteral")
  }

  fun `test quickfix - allow a single suppression from comment`() {
    testAllowSuppressionQuickFix(JvmLanguage.JAVA, """
          public class A {
            //noinspection Public<caret>Field
            public String s = "test";
          }
        """.trimIndent(), "PublicField")
  }

  fun `test quickfix - allow multiple suppressions from comment`() {
    testAllowSuppressionQuickFix(JvmLanguage.JAVA, """
          public class A {
            //noinspection Public<caret>Field, Hard<caret>CodedStringLiteral
            public String s = "test";
          }
        """.trimIndent(), "PublicField", "HardCodedStringLiteral")
  }
}
