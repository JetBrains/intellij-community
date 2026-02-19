// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.java.refactoring.suggested

import com.intellij.refactoring.suggested.BaseSignatureChangePresentationTest
import com.intellij.refactoring.suggested.SuggestedRefactoringSupport
import com.intellij.refactoring.suggested.SuggestedRefactoringSupport.Parameter
import com.intellij.refactoring.suggested.SuggestedRefactoringSupport.Signature

class JavaSignatureChangePresentationTest : BaseSignatureChangePresentationTest() {
  override val refactoringSupport = JavaSuggestedRefactoringSupport()

  private fun signature(
    name: String,
    type: String?,
    parameters: List<Parameter>,
    additionalData: SuggestedRefactoringSupport.SignatureAdditionalData? = null
  ): Signature? {
    return Signature.create(name, type, parameters, additionalData)
  }

  fun testAddParameters() {
    val oldSignature = signature(
      "foo",
      "String",
      listOf(Parameter(0, "p1", "Int"))
    )!!
    val newSignature = signature(
      "foo",
      "String",
      listOf(
        Parameter(Any(), "p0", "Any"),
        Parameter(0, "p1", "Int"),
        Parameter(Any(), "p2", "Long")
      )
    )!!
    doTest(
      oldSignature,
      newSignature,
      """
        Old:
          'String'
          ' '
          'foo'
          '('
          LineBreak('', true)
          Group:
            'Int'
            ' '
            'p1'
          LineBreak('', false)
          ')'
        New:
          'String'
          ' '
          'foo'
          '('
          LineBreak('', true)
          Group (added):
            'Any'
            ' '
            'p0'
          ','
          LineBreak(' ', true)
          Group:
            'Int'
            ' '
            'p1'
          ','
          LineBreak(' ', true)
          Group (added):
            'Long'
            ' '
            'p2'
          LineBreak('', false)
          ')'
      """.trimIndent()
    )
  }

  fun testSwapParameters() {
    val oldSignature = signature(
      "foo",
      "void",
      listOf(
        Parameter(0, "p1", "Int"),
        Parameter(1, "p2", "Long")
      )
    )!!
    val newSignature = signature(
      "foo",
      "void",
      listOf(
        Parameter(1, "p2", "Long"),
        Parameter(0, "p1", "Int")
      )
    )!!
    doTest(
      oldSignature,
      newSignature,
      """
        Old:
          'void'
          ' '
          'foo'
          '('
          LineBreak('', true)
          Group (moved):
            'Int'
            ' '
            'p1'
          ','
          LineBreak(' ', true)
          Group:
            'Long'
            ' '
            'p2'
          LineBreak('', false)
          ')'
        New:
          'void'
          ' '
          'foo'
          '('
          LineBreak('', true)
          Group:
            'Long'
            ' '
            'p2'
          ','
          LineBreak(' ', true)
          Group (moved):
            'Int'
            ' '
            'p1'
          LineBreak('', false)
          ')'
      """.trimIndent()
    )
  }

  fun testSwapParametersAndRenameFirst() {
    val oldSignature = signature(
      "foo",
      "void",
      listOf(
        Parameter(0, "p1", "Int"),
        Parameter(1, "p2", "Long")
      )
    )!!
    val newSignature = signature(
      "foo",
      "void",
      listOf(
        Parameter(1, "p2", "Long"),
        Parameter(0, "p1New", "Int")
      )
    )!!
    doTest(
      oldSignature,
      newSignature,
      """
        Old:
          'void'
          ' '
          'foo'
          '('
          LineBreak('', true)
          Group (moved):
            'Int'
            ' '
            'p1' (modified)
          ','
          LineBreak(' ', true)
          Group:
            'Long'
            ' '
            'p2'
          LineBreak('', false)
          ')'
        New:
          'void'
          ' '
          'foo'
          '('
          LineBreak('', true)
          Group:
            'Long'
            ' '
            'p2'
          ','
          LineBreak(' ', true)
          Group (moved):
            'Int'
            ' '
            'p1New' (modified)
          LineBreak('', false)
          ')'
      """.trimIndent()
    )
  }

  fun testSwapParametersAndRenameSecond() {
    val oldSignature = signature(
      "foo",
      "void",
      listOf(
        Parameter(0, "p1", "Int"),
        Parameter(1, "p2", "Long")
      )
    )!!
    val newSignature = signature(
      "foo",
      "void",
      listOf(
        Parameter(1, "p2New", "Long"),
        Parameter(0, "p1", "Int")
      )
    )!!
    doTest(
      oldSignature,
      newSignature,
      """
        Old:
          'void'
          ' '
          'foo'
          '('
          LineBreak('', true)
          Group:
            'Int'
            ' '
            'p1'
          ','
          LineBreak(' ', true)
          Group (moved):
            'Long'
            ' '
            'p2' (modified)
          LineBreak('', false)
          ')'
        New:
          'void'
          ' '
          'foo'
          '('
          LineBreak('', true)
          Group (moved):
            'Long'
            ' '
            'p2New' (modified)
          ','
          LineBreak(' ', true)
          Group:
            'Int'
            ' '
            'p1'
          LineBreak('', false)
          ')'
      """.trimIndent()
    )
  }

  fun testSwapParametersAndAddNewOne() {
    val oldSignature = signature(
      "foo",
      "void",
      listOf(
        Parameter(0, "p1", "Int"),
        Parameter(1, "p2", "Long")
      )
    )!!
    val newSignature = signature(
      "foo",
      "void",
      listOf(
        Parameter(1, "p2", "Long"),
        Parameter(0, "p1", "Int"),
        Parameter(2, "pNew", "String")
      )
    )!!
    doTest(
      oldSignature,
      newSignature,
      """
        Old:
          'void'
          ' '
          'foo'
          '('
          LineBreak('', true)
          Group (id = 0, moved):
            'Int'
            ' '
            'p1'
          ','
          LineBreak(' ', true)
          Group:
            'Long'
            ' '
            'p2'
          LineBreak('', false)
          ')'
        New:
          'void'
          ' '
          'foo'
          '('
          LineBreak('', true)
          Group:
            'Long'
            ' '
            'p2'
          ','
          LineBreak(' ', true)
          Group (id = 0, moved):
            'Int'
            ' '
            'p1'
          ','
          LineBreak(' ', true)
          Group (added):
            'String'
            ' '
            'pNew'
          LineBreak('', false)
          ')'
      """.trimIndent()
    )
  }

  fun testSwapParametersAndRemoveParameter() {
    val oldSignature = signature(
      "foo",
      "void",
      listOf(
        Parameter(0, "p1", "Int"),
        Parameter(1, "p2", "Long"),
        Parameter(2, "p", "String")
      )
    )!!
    val newSignature = signature(
      "foo",
      "void",
      listOf(
        Parameter(1, "p2", "Long"),
        Parameter(0, "p1", "Int")
      )
    )!!
    doTest(
      oldSignature,
      newSignature,
      """
        Old:
          'void'
          ' '
          'foo'
          '('
          LineBreak('', true)
          Group (id = 0, moved):
            'Int'
            ' '
            'p1'
          ','
          LineBreak(' ', true)
          Group:
            'Long'
            ' '
            'p2'
          ','
          LineBreak(' ', true)
          Group (removed):
            'String'
            ' '
            'p'
          LineBreak('', false)
          ')'
        New:
          'void'
          ' '
          'foo'
          '('
          LineBreak('', true)
          Group:
            'Long'
            ' '
            'p2'
          ','
          LineBreak(' ', true)
          Group (id = 0, moved):
            'Int'
            ' '
            'p1'
          LineBreak('', false)
          ')'
      """.trimIndent()
    )
  }

  fun testSwapParametersAndAddRemoveAnnotations() {
    val oldSignature = signature(
      "foo",
      "void",
      listOf(
        Parameter(0, "p1", "Int", JavaParameterAdditionalData("@NotNull")),
        Parameter(1, "p2", "Int"),
        Parameter(2, "p3", "Int"),
        Parameter(3, "p4", "Int", JavaParameterAdditionalData("@NotNull")),
        Parameter(4, "p5", "Int"),
        Parameter(5, "p6", "Int"),
        Parameter(6, "p7", "Int"),
        Parameter(7, "p8", "Int")
      )
    )!!
    val newSignature = signature(
      "foo",
      "void",
      listOf(
        Parameter(1, "p2", "Int"),
        Parameter(0, "p1", "Int"),
        Parameter(3, "p4", "Int"),
        Parameter(2, "p3", "Int"),
        Parameter(5, "p6", "Int"),
        Parameter(4, "p5", "Int", JavaParameterAdditionalData("@Nullable")),
        Parameter(7, "p8", "Int", JavaParameterAdditionalData("@Nullable")),
        Parameter(6, "p7", "Int")
      )
    )!!
    doTest(
      oldSignature,
      newSignature,
      """
        Old:
          'void'
          ' '
          'foo'
          '('
          LineBreak('', true)
          Group (id = 0, moved):
            '@NotNull' (removed)
            ' '
            'Int'
            ' '
            'p1'
          ','
          LineBreak(' ', true)
          Group:
            'Int'
            ' '
            'p2'
          ','
          LineBreak(' ', true)
          Group:
            'Int'
            ' '
            'p3'
          ','
          LineBreak(' ', true)
          Group (id = 3, moved):
            '@NotNull' (removed)
            ' '
            'Int'
            ' '
            'p4'
          ','
          LineBreak(' ', true)
          Group (id = 4, moved):
            'Int'
            ' '
            'p5'
          ','
          LineBreak(' ', true)
          Group:
            'Int'
            ' '
            'p6'
          ','
          LineBreak(' ', true)
          Group:
            'Int'
            ' '
            'p7'
          ','
          LineBreak(' ', true)
          Group (id = 7, moved):
            'Int'
            ' '
            'p8'
          LineBreak('', false)
          ')'
        New:
          'void'
          ' '
          'foo'
          '('
          LineBreak('', true)
          Group:
            'Int'
            ' '
            'p2'
          ','
          LineBreak(' ', true)
          Group (id = 0, moved):
            'Int'
            ' '
            'p1'
          ','
          LineBreak(' ', true)
          Group (id = 3, moved):
            'Int'
            ' '
            'p4'
          ','
          LineBreak(' ', true)
          Group:
            'Int'
            ' '
            'p3'
          ','
          LineBreak(' ', true)
          Group:
            'Int'
            ' '
            'p6'
          ','
          LineBreak(' ', true)
          Group (id = 4, moved):
            '@Nullable' (added)
            ' '
            'Int'
            ' '
            'p5'
          ','
          LineBreak(' ', true)
          Group (id = 7, moved):
            '@Nullable' (added)
            ' '
            'Int'
            ' '
            'p8'
          ','
          LineBreak(' ', true)
          Group:
            'Int'
            ' '
            'p7'
          LineBreak('', false)
          ')'
        """.trimIndent()
    )
  }

  fun testChangeFunctionName() {
    val oldSignature = signature("foo", "void", emptyList())!!
    val newSignature = signature("bar", "void", emptyList())!!
    doTest(
      oldSignature,
      newSignature,
      """
        Old:
          'void'
          ' '
          'foo' (modified)
          '('
          LineBreak('', false)
          ')'
        New:
          'void'
          ' '
          'bar' (modified)
          '('
          LineBreak('', false)
          ')'
      """.trimIndent()
    )
  }

  fun testChangeReturnType() {
    val oldSignature = signature("foo", "Object", emptyList())!!
    val newSignature = signature("foo", "String", emptyList())!!
    doTest(
      oldSignature,
      newSignature,
      """
        Old:
          'Object' (modified)
          ' '
          'foo'
          '('
          LineBreak('', false)
          ')'
        New:
          'String' (modified)
          ' '
          'foo'
          '('
          LineBreak('', false)
          ')'
      """.trimIndent()
    )
  }

  fun testChangeParameterName() {
    val oldSignature = signature(
      "foo",
      "void",
      listOf(
        Parameter(0, "p1", "int"),
        Parameter(1, "p2", "long")
      )
    )!!
    val newSignature = signature(
      "foo",
      "void",
      listOf(
        Parameter(0, "p1New", "int"),
        Parameter(1, "p2", "long")
      )
    )!!
    doTest(
      oldSignature,
      newSignature,
      """
        Old:
          'void'
          ' '
          'foo'
          '('
          LineBreak('', true)
          Group:
            'int'
            ' '
            'p1' (modified)
          ','
          LineBreak(' ', true)
          Group:
            'long'
            ' '
            'p2'
          LineBreak('', false)
          ')'
        New:
          'void'
          ' '
          'foo'
          '('
          LineBreak('', true)
          Group:
            'int'
            ' '
            'p1New' (modified)
          ','
          LineBreak(' ', true)
          Group:
            'long'
            ' '
            'p2'
          LineBreak('', false)
          ')'
      """.trimIndent()
    )
  }

  fun testChangeTwoParameterNames() {
    val oldSignature = signature(
      "foo",
      "void",
      listOf(
        Parameter(0, "p1", "int"),
        Parameter(1, "p2", "long")
      )
    )!!
    val newSignature = signature(
      "foo",
      "void",
      listOf(
        Parameter(0, "p1New", "int"),
        Parameter(1, "p2New", "long")
      )
    )!!
    doTest(
      oldSignature,
      newSignature,
      """
        Old:
          'void'
          ' '
          'foo'
          '('
          LineBreak('', true)
          Group:
            'int'
            ' '
            'p1' (modified)
          ','
          LineBreak(' ', true)
          Group:
            'long'
            ' '
            'p2' (modified)
          LineBreak('', false)
          ')'
        New:
          'void'
          ' '
          'foo'
          '('
          LineBreak('', true)
          Group:
            'int'
            ' '
            'p1New' (modified)
          ','
          LineBreak(' ', true)
          Group:
            'long'
            ' '
            'p2New' (modified)
          LineBreak('', false)
          ')'
      """.trimIndent()
    )
  }

  fun testMoveAndRenameParameter() {
    val oldSignature = signature(
      "foo",
      "void",
      listOf(
        Parameter(0, "p1", "int"),
        Parameter(1, "p2", "long"),
        Parameter(2, "p3", "Object")
      )
    )!!
    val newSignature = signature(
      "foo",
      "void",
      listOf(
        Parameter(1, "p2", "long"),
        Parameter(2, "p3", "Object"),
        Parameter(0, "p1New", "int")
      )
    )!!
    doTest(
      oldSignature,
      newSignature,
      """
      Old:
        'void'
        ' '
        'foo'
        '('
        LineBreak('', true)
        Group (moved):
          'int'
          ' '
          'p1' (modified)
        ','
        LineBreak(' ', true)
        Group:
          'long'
          ' '
          'p2'
        ','
        LineBreak(' ', true)
        Group:
          'Object'
          ' '
          'p3'
        LineBreak('', false)
        ')'
      New:
        'void'
        ' '
        'foo'
        '('
        LineBreak('', true)
        Group:
          'long'
          ' '
          'p2'
        ','
        LineBreak(' ', true)
        Group:
          'Object'
          ' '
          'p3'
        ','
        LineBreak(' ', true)
        Group (moved):
          'int'
          ' '
          'p1New' (modified)
        LineBreak('', false)
        ')'
      """.trimIndent()
    )
  }

  fun testMoveParameterAndAddAnnotation() {
    val oldSignature = signature(
      "foo",
      "void",
      listOf(
        Parameter(0, "p1", "int"),
        Parameter(1, "p2", "long"),
        Parameter(2, "p3", "Object")
      )
    )!!
    val newSignature = signature(
      "foo",
      "void",
      listOf(
        Parameter(2, "p3", "Object", JavaParameterAdditionalData("@NotNull")),
        Parameter(0, "p1", "int"),
        Parameter(1, "p2", "long")
      )
    )!!
    doTest(
      oldSignature,
      newSignature,
      """
        Old:
          'void'
          ' '
          'foo'
          '('
          LineBreak('', true)
          Group:
            'int'
            ' '
            'p1'
          ','
          LineBreak(' ', true)
          Group:
            'long'
            ' '
            'p2'
          ','
          LineBreak(' ', true)
          Group (moved):
            'Object'
            ' '
            'p3'
          LineBreak('', false)
          ')'
        New:
          'void'
          ' '
          'foo'
          '('
          LineBreak('', true)
          Group (moved):
            '@NotNull' (added)
            ' '
            'Object'
            ' '
            'p3'
          ','
          LineBreak(' ', true)
          Group:
            'int'
            ' '
            'p1'
          ','
          LineBreak(' ', true)
          Group:
            'long'
            ' '
            'p2'
          LineBreak('', false)
          ')'
      """.trimIndent()
    )
  }

  fun testMoveParameterAndRemoveAnnotation() {
    val oldSignature = signature(
      "foo",
      "void",
      listOf(
        Parameter(0, "p1", "int"),
        Parameter(1, "p2", "long"),
        Parameter(2, "p3", "Object", JavaParameterAdditionalData("@NotNull"))
      )
    )!!
    val newSignature = signature(
      "foo",
      "void",
      listOf(
        Parameter(2, "p3", "Object"),
        Parameter(0, "p1", "int"),
        Parameter(1, "p2", "long")
      )
    )!!
    doTest(
      oldSignature,
      newSignature,
      """
        Old:
          'void'
          ' '
          'foo'
          '('
          LineBreak('', true)
          Group:
            'int'
            ' '
            'p1'
          ','
          LineBreak(' ', true)
          Group:
            'long'
            ' '
            'p2'
          ','
          LineBreak(' ', true)
          Group (moved):
            '@NotNull' (removed)
            ' '
            'Object'
            ' '
            'p3'
          LineBreak('', false)
          ')'
        New:
          'void'
          ' '
          'foo'
          '('
          LineBreak('', true)
          Group (moved):
            'Object'
            ' '
            'p3'
          ','
          LineBreak(' ', true)
          Group:
            'int'
            ' '
            'p1'
          ','
          LineBreak(' ', true)
          Group:
            'long'
            ' '
            'p2'
          LineBreak('', false)
          ')'
      """.trimIndent()
    )
  }

  fun testAddParameterToConstructor() {
    val oldSignature = signature(
      "Foo",
      null,
      listOf(Parameter(0, "p1", "Int"))
    )!!
    val newSignature = signature(
      "Foo",
      null,
      listOf(
        Parameter(Any(), "p0", "Any"),
        Parameter(0, "p1", "Int")
      )
    )!!
    doTest(
      oldSignature,
      newSignature,
      """
        Old:
          'Foo'
          '('
          LineBreak('', true)
          Group:
            'Int'
            ' '
            'p1'
          LineBreak('', false)
          ')'
        New:
          'Foo'
          '('
          LineBreak('', true)
          Group (added):
            'Any'
            ' '
            'p0'
          ','
          LineBreak(' ', true)
          Group:
            'Int'
            ' '
            'p1'
          LineBreak('', false)
          ')'
      """.trimIndent()
    )
  }

  fun testAnnotations() {
    val oldSignature = signature(
      "foo",
      "void",
      listOf(
        Parameter(0, "p1", "String", JavaParameterAdditionalData("@Nullable")),
        Parameter(1, "p2", "String"),
        Parameter(2, "p3", "Object", JavaParameterAdditionalData("@Nullable"))
      ),
      JavaDeclarationAdditionalData("public", "", emptyList())
    )!!
    val newSignature = signature(
      "foo",
      "Object",
      listOf(
        Parameter(1, "p2", "String", JavaParameterAdditionalData("@NotNull")),
        Parameter(2, "p3", "Object"),
        Parameter(0, "p1New", "String", JavaParameterAdditionalData("@NotNull"))
      ),
      JavaDeclarationAdditionalData("public", "@NotNull", emptyList())
    )!!
    doTest(
      oldSignature,
      newSignature,
      """
        Old:
          'void' (modified)
          ' '
          'foo'
          '('
          LineBreak('', true)
          Group (id = 0, moved):
            '@Nullable' (modified)
            ' '
            'String'
            ' '
            'p1' (modified)
          ','
          LineBreak(' ', true)
          Group:
            'String'
            ' '
            'p2'
          ','
          LineBreak(' ', true)
          Group:
            '@Nullable' (removed)
            ' '
            'Object'
            ' '
            'p3'
          LineBreak('', false)
          ')'
        New:
          '@NotNull' (added)
          ' '
          'Object' (modified)
          ' '
          'foo'
          '('
          LineBreak('', true)
          Group:
            '@NotNull' (added)
            ' '
            'String'
            ' '
            'p2'
          ','
          LineBreak(' ', true)
          Group:
            'Object'
            ' '
            'p3'
          ','
          LineBreak(' ', true)
          Group (id = 0, moved):
            '@NotNull' (modified)
            ' '
            'String'
            ' '
            'p1New' (modified)
          LineBreak('', false)
          ')'
      """.trimIndent()
    )
  }

  fun testReorderExceptions() {
    val oldSignature = signature(
      "foo",
      "void",
      emptyList(),
      JavaDeclarationAdditionalData("public", "", listOf("IOException", "SQLException", "NumberFormatException"))
    )!!
    val newSignature = signature(
      "foo",
      "void",
      emptyList(),
      JavaDeclarationAdditionalData("public", "", listOf("NumberFormatException", "IOException", "SQLException"))
    )!!
    doTest(
      oldSignature,
      newSignature,
      """
        Old:
          'void'
          ' '
          'foo'
          '('
          LineBreak('', false)
          ')'
          LineBreak(' ', false)
          'throws'
          LineBreak(' ', true)
          'IOException'
          ','
          LineBreak(' ', true)
          'SQLException'
          ','
          LineBreak(' ', true)
          'NumberFormatException' (moved)
        New:
          'void'
          ' '
          'foo'
          '('
          LineBreak('', false)
          ')'
          LineBreak(' ', false)
          'throws'
          LineBreak(' ', true)
          'NumberFormatException' (moved)
          ','
          LineBreak(' ', true)
          'IOException'
          ','
          LineBreak(' ', true)
          'SQLException'
      """.trimIndent()
    )
  }

  fun testSwapExceptionsAndParameters() {
    val oldSignature = signature(
      "foo",
      "void",
      listOf(
        Parameter(0, "p1", "String"),
        Parameter(1, "p2", "int")
      ),
      JavaDeclarationAdditionalData("public", "", listOf("IOException", "SQLException"))
    )!!
    val newSignature = signature(
      "foo",
      "void",
      listOf(
        Parameter(1, "p2", "int"),
        Parameter(0, "p1", "String")
      ),
      JavaDeclarationAdditionalData("public", "", listOf("SQLException", "IOException"))
    )!!
    doTest(
      oldSignature,
      newSignature,
      """
        Old:
          'void'
          ' '
          'foo'
          '('
          LineBreak('', true)
          Group (id = 0, moved):
            'String'
            ' '
            'p1'
          ','
          LineBreak(' ', true)
          Group:
            'int'
            ' '
            'p2'
          LineBreak('', false)
          ')'
          LineBreak(' ', false)
          'throws'
          LineBreak(' ', true)
          'IOException' (id = ExceptionConnectionId(oldIndex=0), moved)
          ','
          LineBreak(' ', true)
          'SQLException'
        New:
          'void'
          ' '
          'foo'
          '('
          LineBreak('', true)
          Group:
            'int'
            ' '
            'p2'
          ','
          LineBreak(' ', true)
          Group (id = 0, moved):
            'String'
            ' '
            'p1'
          LineBreak('', false)
          ')'
          LineBreak(' ', false)
          'throws'
          LineBreak(' ', true)
          'SQLException'
          ','
          LineBreak(' ', true)
          'IOException' (id = ExceptionConnectionId(oldIndex=0), moved)
      """.trimIndent()
    )
  }
}