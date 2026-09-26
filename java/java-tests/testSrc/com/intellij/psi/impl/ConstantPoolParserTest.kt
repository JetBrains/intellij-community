// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl

import com.intellij.psi.impl.cache.impl.idCache.ConstantPoolParser
import com.intellij.psi.impl.cache.impl.idCache.JvmIdentifierUtil.collectJvmIdentifiers
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase5
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.readBytes

class ConstantPoolParserTest : LightJavaCodeInsightFixtureTestCase5() {
  private val constantPoolPath: Path by lazy { Paths.get(fixture.testDataPath).resolve("psi").resolve("constantPool") }

  /**
   * Source:
   * ```
   * public class Simple {
   *     String field = "my field";
   *
   *     void method() {
   *
   *     }
   * }
   *
   * ```
   */
  @Test fun testSimple() = doTest("""
    Code
    LineNumberTable
    LocalVariableTable
    Object
    Simple
    SourceFile
    String
    field
    java
    lang
    method
    this
  """.trimIndent())

  /**
   * Source:
   * ```
   * public class WithParameter {
   *     public static void foo(String[] s) {}
   *     public static void bar(Map<String[], List<List<? extends Comparator<String>>>> s) {}
   * }
   * ```
   */
  @Test fun testWithParameter() = doTest("""
    Code
    Comparator
    LineNumberTable
    List
    LocalVariableTable
    LocalVariableTypeTable
    Map
    Object
    Signature
    SourceFile
    String
    WithParameter
    bar
    example
    foo
    java
    lang
    org
    s
    this
    util
  """.trimIndent())

  /**
   * Source:
   * ```
   * @RuntimeAnnotation.RuntimeAnn
   * public class RuntimeAnnotation {
   *     @Retention(RetentionPolicy.RUNTIME)
   *     @Target(ElementType.TYPE)
   *     @interface RuntimeAnn {
   *
   *     }
   * }
   *
   * ```
   *
   */
  @Test fun testRuntimeAnnotation() = doTest("""
    Code
    InnerClasses
    LineNumberTable
    LocalVariableTable
    Object
    RuntimeAnn
    RuntimeAnnotation
    RuntimeAnnotation${'$'}RuntimeAnn
    RuntimeVisibleAnnotations
    SourceFile
    java
    lang
    this
  """.trimIndent())

  /**
   * Source:
   * ```
   * @RuntimeAnnotationWithArgs.RuntimeAnn(justEnum = RuntimeAnnotationWithArgs.MyEnum.Variant2)
   * public class RuntimeAnnotationWithArgs {
   *     @Retention(RetentionPolicy.RUNTIME)
   *     @Target(ElementType.TYPE)
   *     @interface RuntimeAnn {
   *         MyEnum enumWithDefault() default MyEnum.Variant1;
   *         MyEnum justEnum();
   *     }
   *
   *     enum MyEnum {
   *         Variant1,
   *         Variant2,
   *     }
   * }
   * ```
   */
  @Test fun testRuntimeAnnotationWithArgs() = doTest("""
    Code
    InnerClasses
    LineNumberTable
    LocalVariableTable
    MyEnum
    Object
    RuntimeAnn
    RuntimeAnnotationWithArgs
    RuntimeAnnotationWithArgs${'$'}MyEnum
    RuntimeAnnotationWithArgs${'$'}RuntimeAnn
    RuntimeVisibleAnnotations
    SourceFile
    Variant2
    java
    justEnum
    lang
    this
  """.trimIndent())

  /**
   * Source:
   * ```
   * package pack;
   *
   * public @interface AnnotationDeclaration {
   *     String stringValue() default "my default value";
   *     MyEnum enumValue();
   *
   *     enum MyEnum {
   *         Variant1,
   *         Variant2,
   *     }
   * }
   * ```
   */
  @Test fun testAnnotationDeclaration() = doTest("""
    Annotation
    AnnotationDeclaration
    AnnotationDeclaration${'$'}MyEnum
    AnnotationDefault
    InnerClasses
    MyEnum
    Object
    SourceFile
    String
    annotation
    enumValue
    java
    lang
    pack
    stringValue
  """.trimIndent())

  /**
   * Source:
   * ```
   *public enum MyEnum {
   *     Variant1,
   *     Variant2,
   * }
   * ```
   */
  @Test fun testMyEnum() = doTest("""
    ${'$'}VALUES
    Class
    Code
    Enum
    LineNumberTable
    LocalVariableTable
    MyEnum
    Object
    Signature
    SourceFile
    String
    Variant1
    Variant2
    clone
    java
    lang
    name
    this
    valueOf
    values
  """.trimIndent())

  /**
   * Source:
   * ```
   * public class DoubleAndLongFields {
   *     double d = 10.0;
   *     long l = 4L;
   * }
   * ```
   */
  @Test fun testDoubleAndLongFields() = doTest("""
    Code
    D
    DoubleAndLongFields
    J
    LineNumberTable
    LocalVariableTable
    Object
    SourceFile
    d
    java
    l
    lang
    this
  """.trimIndent())
  /**
   * Source:
   * ```
   * public class TryCatch {
   *     void foo() {
   *         try {
   *             bar();
   *         } catch (RuntimeException e) {
   *
   *         }
   *     }
   *
   *     native void bar();
   * }
   *
   * ```
   */
  @Test fun testTryCatch() = doTest("""
    Code
    LineNumberTable
    LocalVariableTable
    Object
    RuntimeException
    SourceFile
    StackMapTable
    TryCatch
    bar
    foo
    java
    lang
    this
  """.trimIndent())

  /**
   * Source:
   * ```
   * import java.util.List;
   *
   * public class MethodWithExtendsWildcard {
   *     public void id(List<? extends CharSequence> param) {}
   * }
   *
   * ```
   */
  @Test fun testMethodWithExtendsWildcard() {
    doTest("""
      CharSequence
      Code
      LineNumberTable
      List
      LocalVariableTable
      LocalVariableTypeTable
      MethodWithExtendsWildcard
      Object
      Signature
      SourceFile
      id
      java
      lang
      param
      this
      util
    """.trimIndent())
  }

  /**
   * Source:
   * ```
   * import java.util.List;
   *
   * public class MethodWithSuperWildcard {
   *     public void id(List<? super CharSequence> param) {}
   * }
   *
   * ```
   */
  @Test fun testMethodWithSuperWildcard() {
    doTest("""
      CharSequence
      Code
      LineNumberTable
      List
      LocalVariableTable
      LocalVariableTypeTable
      MethodWithSuperWildcard
      Object
      Signature
      SourceFile
      id
      java
      lang
      param
      this
      util
    """.trimIndent())
  }

  /**
   * Source:
   * ```
   * public class MethodWithTypeParam {
   *     public <TYPE_PARAM> void id(TYPE_PARAM param) {}
   * }
   * ```
   */
  @Test fun testMethodWithTypeParam() {
    doTest("""
      Code
      LineNumberTable
      LocalVariableTable
      LocalVariableTypeTable
      MethodWithTypeParam
      Object
      Signature
      SourceFile
      id
      java
      lang
      param
      this
    """.trimIndent())
  }

  /**
   * Source:
   * ```
   * public class MethodWithTypeParamExtends {
   *     public <TYPE_PARAM extends CharSequence> void id(TYPE_PARAM param) {}
   * }
   * ```
   */
  @Test fun testMethodWithTypeParamExtends() {
    doTest("""
      CharSequence
      Code
      LineNumberTable
      LocalVariableTable
      LocalVariableTypeTable
      MethodWithTypeParamExtends
      Object
      Signature
      SourceFile
      id
      java
      lang
      param
      this
    """.trimIndent())
  }

  /**
   * Source:
   * ```
   * import java.util.List;
   *
   * public class MethodWithWildcard {
   *     public void id(List<?> param) {}
   * }
   * ```
   */
  @Test fun testMethodWithWildcard() {
    doTest("""
      Code
      LineNumberTable
      List
      LocalVariableTable
      LocalVariableTypeTable
      MethodWithWildcard
      Object
      Signature
      SourceFile
      id
      java
      lang
      param
      this
      util
    """.trimIndent())
  }

  /**
   * Source:
   * ```
   * public class TypeParameterInClass <TYPE_PARAM extends CharSequence> {}
   * ```
   */
  @Test fun testTypeParameterInClass() {
    doTest("""
      CharSequence
      Code
      LineNumberTable
      LocalVariableTable
      LocalVariableTypeTable
      Object
      Signature
      SourceFile
      TypeParameterInClass
      java
      lang
      this
    """.trimIndent())
  }

  /**
   * Source:
   * ```
   * import java.util.List;
   *
   * public class SeveralBounds<T extends Comparable<String> & List<Integer>> {
   * }
   * ```
   */
  @Test fun testSeveralBounds() {
    doTest("""
      Code
      Comparable
      Integer
      LineNumberTable
      List
      LocalVariableTable
      LocalVariableTypeTable
      Object
      SeveralBounds
      Signature
      SourceFile
      String
      java
      lang
      this
      util
    """.trimIndent())
  }

  /**
   * Source:
   * ```
   * import java.util.List;
   *
   * public class SeveralBoundsInMethod {
   *     <T extends CharSequence & Comparable<Integer>>void foo() {}
   * }
   * ```
   */
  @Test fun testSeveralBoundsInMethod() {
    doTest("""
      Code
      LineNumberTable
      LocalVariableTable
      Object
      SeveralBoundsInMethod
      Signature
      SourceFile
      foo
      java
      lang
      this
    """.trimIndent())
  }

  /**
   * Source:
   * ```
   * import java.util.List;
   *
   * public class Dollar$InClass {
   *     void dollarIn$Method() {}
   *     int $inField;
   * }
   *
   * ```
   */
  @Test fun `testDollar$InClass`() {
    doTest("""
      ${'$'}inField
      Code
      Dollar${'$'}InClass
      I
      LineNumberTable
      LocalVariableTable
      Object
      SourceFile
      dollarIn${'$'}Method
      java
      lang
      this
    """.trimIndent())
  }

  private fun doTest(text: String) {
    val filePath = constantPoolPath.resolve(getTestName(false) + ".class")
    val bytes = filePath.readBytes()
    val ids = extractIdsFromClassFile(bytes)
    Assertions.assertEquals(text.trim(), ids.joinToString("\n").trim())
  }
}

internal fun extractIdsFromClassFile(bytes: ByteArray): List<String> {
  val parts = ArrayList<CharSequence>()
  ConstantPoolParser(DataInputStream(ByteArrayInputStream(bytes))) {
    val tmp = ArrayList<CharSequence>()
    if (collectJvmIdentifiers(it) { sequence: CharSequence, start: Int, end: Int -> tmp.add(sequence.subSequence(start, end)) }) {
      parts.addAll(tmp)
    }
  }.parse()
  val ids = parts.map { it.toString() }.toSet().sorted()
  return ids
}