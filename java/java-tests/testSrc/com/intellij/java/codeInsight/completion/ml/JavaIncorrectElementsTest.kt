// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.completion.ml

import com.intellij.codeInsight.completion.CompletionLocation
import com.intellij.codeInsight.completion.LightCompletionTestCase
import com.intellij.codeInsight.completion.ml.ContextFeatures
import com.intellij.codeInsight.completion.ml.ElementFeatureProvider
import com.intellij.codeInsight.completion.ml.JavaElementFeaturesProvider
import com.intellij.codeInsight.completion.ml.MLFeatureValue
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.lang.java.JavaLanguage
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiKeyword

class JavaIncorrectElementsTest : LightCompletionTestCase() {
  fun `test class extends position`() {
    doTest("""
        final class FinalClass {}
        class Test extends <caret> {}
      """.trimIndent(), mapOf(
      "java.lang.Runnable" to true,
      "java.lang.Thread" to false,
      "java.lang.Override" to true,
      "FinalClass" to true,
    ))
  }

  fun `test interface extends position`() {
    doTest("""interface Test extends <caret> {}""".trimIndent(), mapOf(
      "java.lang.Runnable" to false,
      "java.lang.Thread" to true,
      "java.lang.Override" to false,
    ))
  }

  fun `test class implements position`() {
    doTest("""class Test implements <caret> {}""".trimIndent(), mapOf(
      "java.lang.Runnable" to false,
      "java.lang.Thread" to true,
      "java.lang.Override" to false,
    ))
  }

  fun `test catch clause position`() {
    doTest("""
      import java.io.IOException;

      class Test {
        void test() {
          try {
            
          } catch(<caret>)
        }
      }""".trimIndent(), mapOf(
        "java.lang.Thread" to true,
        "java.lang.Object" to true,
        "java.lang.Exception" to false,
        "java.lang.Error" to false,
        "java.lang.Throwable" to false,
        "java.lang.RuntimeException" to false,
        "java.lang.IllegalArgumentException" to false,
        "java.lang.ClassNotFoundException" to true,
        "java.io.IOException" to true
      ))
  }

  fun `test catch clause with checked exception position`() {
    doTest("""
      import java.io.IOException;

      class Test {
        void test() {
          try {
            throw new IOException();
          } catch(<caret>)
        }
      }""".trimIndent(), mapOf(
        "java.io.IOException" to false
      ))
  }

  fun `test catch clause with unchecked exception position`() {
    doTest("""
      class Test {
        void test() {
          try {
            throw new Error();
          } catch(<caret>)
        }
      }""".trimIndent(), mapOf(
        "java.lang.Thread" to true,
        "java.lang.Object" to true,
        "java.lang.Error" to false,
      ))
  }

  fun `test multi catch type position`() {
    doTest("""
      class Test {
        void test() {
          try {
      
          } catch (IllegalArgumentException | <caret>)
        }
      }""".trimIndent(), mapOf(
        "java.lang.Thread" to true,
        "java.lang.Object" to true,
        "java.lang.Exception" to false,
        "java.lang.Error" to false,
        "java.lang.Throwable" to false,
        "java.lang.RuntimeException" to false,
        "java.lang.IllegalArgumentException" to false,
        "java.lang.ClassNotFoundException" to true,
      ))
  }

  fun `test throws method list position`() {
    doTest("""
      class Test {
        void test() throws <caret> {
        }
      }""".trimIndent(), mapOf(
        "java.lang.Object" to true,
        "java.lang.Thread" to true,
        "java.lang.Exception" to false,
        "java.lang.Error" to false,
        "java.lang.Throwable" to false,
        "java.lang.RuntimeException" to false,
        "java.lang.IllegalArgumentException" to false,
        "java.lang.ClassNotFoundException" to false,
      ))
  }

  fun `test multi throws method list position`() {
    doTest("""
      import java.io.IOException;
      
      class Test {
        void test() throws IOException, <caret> {
        }
      }""".trimIndent(), mapOf(
        "java.lang.Thread" to true,
        "java.lang.Object" to true,
        "java.lang.Exception" to false,
        "java.lang.Error" to false,
        "java.lang.Throwable" to false,
        "java.lang.RuntimeException" to false,
        "java.lang.IllegalArgumentException" to false,
        "java.lang.ClassNotFoundException" to false,
      ))
  }

  fun `test javadoc @throws tag position`() {
    doTest("""
      /**
       * @throws <caret>
       */
      class Test {
        void test() {
        }
      }""".trimIndent(), mapOf(
        "java.lang.Thread" to true,
        "java.lang.Object" to true,
        "java.lang.Exception" to false,
        "java.lang.Error" to false,
        "java.lang.Throwable" to false,
        "java.lang.RuntimeException" to false,
        "java.lang.IllegalArgumentException" to false,
        "java.lang.ClassNotFoundException" to false,
    ))
  }

  fun `test try-in-resources position`() {
    doTest("""
      import java.io.FileInputStream;
      class Test {
        void test() {
          try(<caret>) {
            
          } catch(Exception e) {}
        }
      }""".trimIndent(), mapOf(
        "java.io.FileInputStream" to false,
        "java.lang.AutoCloseable" to false,
        "java.lang.Thread" to true,
        "java.lang.Object" to true,
        "boolean" to true,
      ))
  }

  fun `test multi try-with-resources position`() {
    doTest("""
      import java.util.zip.ZipFile;
      import java.io.IOException;

      class Test {
        void test() {
          try (ZipFile file = new ZipFile(""); <caret>) {
          
          } catch (IOException e) {
            e.printStackTrace();
          }
        }
      }""".trimIndent(), mapOf(
        "java.lang.AutoCloseable" to false,
        "java.util.zip.ZipFile" to false,
        "java.lang.Thread" to true,
        "java.lang.Object" to true,
      ))
  }

  fun `test annotation position`() {
    doTest("""
      class Test {
        @<caret>
        void test() {
        }
      }""".trimIndent(), mapOf(
        "java.lang.Deprecated" to false,
        "java.lang.FunctionalInterface" to true,
      ))
  }

  fun `test type parameter position`() {
    doTest("""
      import java.io.Serializable;
      class Test<T extends Serializable> {
        static void test() {
          new Test<<caret>>();
        }
      }""".trimIndent(), mapOf(
        "java.lang.Runtime" to true,
        "java.lang.String" to false,
        "boolean" to true,
      ))
  }

  private fun doTest(text: String, assertNames: Map<String, Boolean>) {
    val assertedNames = assertNames.toMutableMap()

    val overrideProvider = object: ElementFeatureProvider {
      private val original = JavaElementFeaturesProvider()

      override fun getName(): String = original.name
      override fun calculateFeatures(element: LookupElement,
                                     location: CompletionLocation,
                                     contextFeatures: ContextFeatures): Map<String, MLFeatureValue> {
        val elementFeatures = original.calculateFeatures(element, location, contextFeatures)
        val actual = (elementFeatures["incorrect_element"]?.value ?: false) as Boolean
        val obj = element.`object`
        val keyName = when {
          obj is PsiKeyword -> obj.text!!
          obj is PsiClass && obj.qualifiedName != null -> obj.qualifiedName!!
          else -> return elementFeatures
        }

        val expected = assertedNames[keyName] ?: return elementFeatures
        assertEquals(keyName, expected, actual)
        assertedNames.remove(keyName)
        return elementFeatures
      }
    }

    try {
      ElementFeatureProvider.EP_NAME.addExplicitExtension(JavaLanguage.INSTANCE, overrideProvider)
      configureFromFileText("test.java", text)
      complete()
      assertTrue("Lookup doesn't contain next elements: ${assertedNames.keys.joinToString()}", assertedNames.isEmpty())
    }
    finally {
      ElementFeatureProvider.EP_NAME.removeExplicitExtension(JavaLanguage.INSTANCE, overrideProvider)
    }
  }
}