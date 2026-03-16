// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.analysis.bytecode

import com.intellij.compiler.JavaInMemoryCompiler
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.rules.TempDirectoryExtension
import org.assertj.core.api.Assertions.assertThat
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.nio.file.Path
import kotlin.io.path.createParentDirectories
import kotlin.io.path.writeBytes

@TestApplication
internal class JvmBytecodeReferenceAnalysisTest {
  @JvmField
  @RegisterExtension
  val tempDirectory = TempDirectoryExtension()

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
      emptyMap(),
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

  @Test
  fun `test implicit references to superclasses from declaration`() {
    assertReference(
      sources = mapOf(
        "Main" to "public class Main extends p.A {}"
      ),
      sourcesToSearchImplicitReferences = mapOf(
        "p.A" to "package p; public class A extends Exception {}",
      ),
      "p/A", "java/lang/Exception",
    )
  }

  @Test
  fun `test implicit references to superclasses from method call`() {
    assertReference(
      sources = mapOf(
        "p1.A" to "package p1; public class A extends p2.B { public void foo() {} }",
        "Main" to "public class Main { Main() { new p1.A().foo(); } }"
      ),
      sourcesToSearchImplicitReferences = mapOf(
        "p2.B" to "package p2; public class B {}",
      ),
      "p1/A", "p2/B",
    )
  }

  @Test
  fun `test no implicit references to superclasses if instance is just passed around`() {
    assertReference(
      sources = mapOf(
        "p1.A" to "package p1; public class A extends p2.B { public void foo() {} }",
        "p1.AManager" to """
          |package p1;
          |public class AManager { 
          |  public A createInstance() { return new A(); }
          |  public void useInstance(A a) { a.foo();  }
          |}
          """.trimMargin(),
        "Main" to """
          |import p1.*;
          |public class Main { 
          |  Main() { 
          |    AManager manager = new AManager();
          |    A a = manager.createInstance();
          |    manager.useInstance(a); 
          |  } 
          |}
          """.trimMargin()
      ),
      sourcesToSearchImplicitReferences = mapOf(
        "p2.B" to "package p2; public class B {}",
      ),
      "p1/A", "p1/AManager",
    )
  }

  @Test
  fun `test implicit references to superclasses in generic parameters from declaration`() {
    assertReference(
      sources = mapOf(
        "Main" to "public abstract class Main extends p.A {}"
      ),
      sourcesToSearchImplicitReferences = mapOf(
        "p.A" to "package p; public abstract class A extends B<String> {}",
        "p.B" to "package p; import java.util.*; public abstract class B<T> implements Map<T, List<T>> {}"
      ),
      "p/A", "p/B", "java/util/Map", "java/util/List", "java/lang/String",
    )
  }

  @Test
  fun `test avoid StackOverflowError when processing implicit references to superclasses`() {
    assertReference(
      sources = mapOf(
        "Main" to "public abstract class Main extends p.A {}"
      ),
      sourcesToSearchImplicitReferences = mapOf(
        "p.A" to "package p; public class A extends B<A> {}",
        "p.B" to "package p; public class B<T> {}"
      ),
      "p/A", "p/B",
    )
  }


  private fun assertReference(@Language("JAVA") source: String, vararg expectedTargets: String) {
    assertReference(
      sources = mapOf("Main" to source),
      sourcesToSearchImplicitReferences = emptyMap(),
      expectedTargets = expectedTargets,
    )
  }

  private fun assertReference(sources: Map<String, String>,
                              sourcesToSearchImplicitReferences: Map<String, String> = emptyMap(),
                              vararg expectedTargets: String) {
    val classpathForImplicitReferences = ArrayList<Path>()
    if (sourcesToSearchImplicitReferences.isNotEmpty()) {
      saveClassFiles(JavaInMemoryCompiler().compile(sourcesToSearchImplicitReferences))
      classpathForImplicitReferences.add(tempDirectory.rootPath)
    }
    val compiler = JavaInMemoryCompiler(*classpathForImplicitReferences.map { it.toFile() }.toTypedArray())
    val compiledClasses = compiler.compile(sources)
    if (sourcesToSearchImplicitReferences.isNotEmpty()) {
      saveClassFiles(compiledClasses)
    }
    val mainClassBytes = compiledClasses["Main"] ?: throw IllegalStateException("Main class not found")

    val analysis = JvmBytecodeAnalysis.getInstance()
    val references = mutableSetOf<String>()
    val referenceProcessor = object : JvmBytecodeReferenceProcessor {
      override fun processClassReference(targetClass: JvmClassBytecodeDeclaration, sourceClass: JvmClassBytecodeDeclaration) {
        references.add(targetClass.binaryClassName)
      }
    }

    val analyzer =
      if (classpathForImplicitReferences.isEmpty()) analysis.createReferenceAnalyzer(referenceProcessor)
      else analysis.createReferenceAnalyzerWithImplicitSuperclassReferences(referenceProcessor, classpathForImplicitReferences)
    analyzer.processFileContent(mainClassBytes)
    references.remove("java/lang/Object") //always referenced as the superclass
    assertThat(references).containsExactlyInAnyOrder(*expectedTargets)
  }

  private fun saveClassFiles(classFiles: Map<String, ByteArray>) {
    for ((className, classFileContent) in classFiles) {
      val classFilePath = tempDirectory.rootPath.resolve("${className.replace('.', '/')}.class")
      classFilePath.createParentDirectories()
      classFilePath.writeBytes(classFileContent)
    }
  }
}
