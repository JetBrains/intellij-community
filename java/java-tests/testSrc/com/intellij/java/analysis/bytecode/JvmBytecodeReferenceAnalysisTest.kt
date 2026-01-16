// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.analysis.bytecode

import com.intellij.compiler.JavaInMemoryCompiler
import com.intellij.testFramework.junit5.TestApplication
import org.assertj.core.api.Assertions.assertThat
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Test

@TestApplication
internal class JvmBytecodeReferenceAnalysisTest {
  @Test
  fun `test field reference`() {
    assertReference("public class Main { String field; }", "java/lang/String")
  }

  @Test
  fun `test super class reference`() {
    assertReference("public class Main extends Exception { }", "java/lang/Exception")
  }

  @Test
  fun `test interface reference`() {
    assertReference("public abstract class Main implements Runnable { }", "java/lang/Runnable")
  }

  @Test
  fun `test method parameter reference`() {
    assertReference("public class Main { void method(String p) {} }", "java/lang/String")
  }

  @Test
  fun `test method return type reference`() {
    assertReference("public class Main { String method() { return null; } }", "java/lang/String")
  }

  @Suppress("RedundantThrows")
  @Test
  fun `test method exception reference`() {
    assertReference("public class Main { void method() throws Exception {} }", "java/lang/Exception")
  }

  @Test
  fun `test annotation reference`() {
    assertReference("@Deprecated public class Main { }", "java/lang/Deprecated")
  }

  @Test
  fun `test annotation value reference`() {
    assertReference(
      mapOf(
        "A" to "public @interface A { Class<?> value(); }",
        "Main" to "@A(String.class) public class Main { }"
      ),
      "A", "java/lang/String"
    )
  }

  @Test
  fun `test enum annotation value reference`() {
    assertReference(
      """
        |import java.lang.annotation.Retention;
        |import java.lang.annotation.RetentionPolicy;
        |@Retention(RetentionPolicy.RUNTIME) 
        |public @interface Main { }
        """.trimMargin(),
      "java/lang/annotation/Annotation", "java/lang/annotation/Retention", "java/lang/annotation/RetentionPolicy"
    )
  }

  @Test
  fun `test constructor reference`() {
    assertReference("public class Main { Object field = new String(); }", "java/lang/String")
  }

  @Test
  fun `test method call reference`() {
    assertReference("public class Main { void method() { System.out.println(\"\"); } }",
                    "java/lang/System", "java/io/PrintStream", "java/lang/String")
  }

  @Suppress("AccessStaticViaInstance")
  @Test
  fun `test field access reference`() {
    assertReference("public class Main { void method() { String s = \"\"; int i = s.CASE_INSENSITIVE_ORDER.hashCode(); } }",
                    "java/lang/String",
                    "java/util/Comparator")
  }

  @Suppress("ResultOfObjectAllocationIgnored", "CatchMayIgnoreException")
  @Test
  fun `test catch reference`() {
    assertReference("public class Main { void method() { try { new Object(); } catch (Exception e) { } } }",
                    "java/lang/Exception")
  }

  @Test
  fun `test class constant reference`() {
    assertReference("public class Main { Class<?> c = String.class; }",
                    "java/lang/Class", "java/lang/String")
  }

  @Test
  fun `test generic field reference`() {
    assertReference("import java.util.List; public class Main { List<String> field; }",
                    "java/util/List", "java/lang/String")
  }

  @Test
  fun `test generic method parameter reference`() {
    assertReference("import java.util.List; public class Main { void method(List<String> p) {} }",
                    "java/util/List", "java/lang/String")
  }

  @Test
  fun `test generic method return type reference`() {
    assertReference("import java.util.List; public class Main { List<String> method() { return null; } }",
                    "java/util/List", "java/lang/String")
  }

  @Test
  fun `test type annotation reference`() {
    assertReference("import java.lang.annotation.*; @Target(ElementType.TYPE_USE) @interface A {} public class Main { @A String field; }",
                    "java/lang/String",
                    "A")
  }

  private fun assertReference(@Language("JAVA") source: String, vararg expectedTargets: String) {
    assertReference(mapOf("Main" to source), *expectedTargets)
  }

  private fun assertReference(sources: Map<String, String>, vararg expectedTargets: String) {
    val compiler = JavaInMemoryCompiler()
    val compiledClasses = compiler.compile(sources)
    val mainClassBytes = compiledClasses["Main"] ?: throw IllegalStateException("Main class not found")

    val analysis = JvmBytecodeAnalysis.getInstance()
    val references = mutableSetOf<String>()
    val referenceProcessor = object : JvmBytecodeReferenceProcessor {
      override fun processClassReference(targetClass: JvmClassBytecodeDeclaration, sourceClass: JvmClassBytecodeDeclaration) {
        references.add(targetClass.binaryClassName)
      }
    }

    val analyzer = analysis.createReferenceAnalyzer(referenceProcessor)
    analyzer.processFileContent(mainClassBytes)
    references.remove("java/lang/Object") //always referenced as the superclass
    assertThat(references).containsExactlyInAnyOrder(*expectedTargets)
  }
}
