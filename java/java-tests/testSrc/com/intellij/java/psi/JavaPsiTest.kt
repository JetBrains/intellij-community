// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.psi

import com.intellij.openapi.application.readAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.diagnostic.DefaultLogger
import com.intellij.psi.CommonClassNames
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiDeconstructionPattern
import com.intellij.psi.PsiElementFactory
import com.intellij.psi.PsiExpressionStatement
import com.intellij.psi.PsiForeachPatternStatement
import com.intellij.psi.PsiForeachStatement
import com.intellij.psi.PsiImplicitClass
import com.intellij.psi.PsiInstanceOfExpression
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiLiteralExpression
import com.intellij.psi.PsiModifier
import com.intellij.psi.PsiPatternVariable
import com.intellij.psi.PsiReturnStatement
import com.intellij.psi.PsiSubstitutor
import com.intellij.psi.PsiType
import com.intellij.psi.PsiTypeTestPattern
import com.intellij.psi.PsiTypes
import com.intellij.psi.impl.source.PsiClassReferenceType
import com.intellij.psi.impl.source.PsiImmediateClassType
import com.intellij.psi.javadoc.PsiDocComment
import com.intellij.psi.javadoc.PsiDocTag
import com.intellij.psi.javadoc.PsiSnippetDocTag
import com.intellij.psi.util.InheritanceUtil
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.TestLoggerFactory
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.fixtures.lightJavaCodeInsightFixture
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.rethrowLoggedErrorsIn
import com.intellij.util.IdempotenceChecker
import com.intellij.util.ThrowableRunnable
import org.assertj.core.api.Assertions.assertThat
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.minutes

@TestApplication
class JavaPsiTest {
  companion object {
    private val projectFixture = lightJavaProjectFixture()
  }

  private val myFixture by lightJavaCodeInsightFixture(projectFixture)

  @Test
  fun testEmptyImportList(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
    val emptyFile = configureFile("")
    assert(readAction { emptyFile.importList } != null)
    val classFile = configureFile("class C { }")
    assert(readAction { classFile.importList } != null)
    val moduleFile = configureFile("module M { }")
    assert(readAction { moduleFile.importList } != null)
  }

  @Test
  fun testModuleInfo(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
    val file = configureFile("module M { }")
    readAction {
      assert(file.packageName == "")
      val module = file.moduleDeclaration
      require(module != null)
      assert(module.name == "M")
      assert(module.modifierList != null)
    }
  }

  @Test
  fun testInstanceOf(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
    val file = configureFile("class A { void foo(A a) { a instanceof B x; } }")
    val expression = readAction {
      (file.classes[0].methods[0].body!!.statements[0] as PsiExpressionStatement).expression as PsiInstanceOfExpression
    }
    val variable = readAction {
      val pattern = expression.pattern
      assert(pattern != null)
      val typeTestPattern = pattern as PsiTypeTestPattern
      val variable = pattern.patternVariable
      require(variable != null)
      assert(variable.name == "x")
      assert(typeTestPattern.checkType!!.text == "B")
      variable
    }
    WriteCommandAction.runWriteCommandAction(myFixture.project) {
      variable.setName("bar")
      assert(expression.text == "a instanceof B bar")
    }
  }

  @Test
  fun testPackageAccessDirectiveTargetInsertion(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
    val file = configureFile("module M { opens pkg; }")
    val statement = readAction { file.moduleDeclaration!!.opens.first() }
    val facade = myFixture.javaFacade.parserFacade
    runCommand { statement.add(facade.createModuleReferenceFromText("M1", null)) }
    assert(readAction { statement.text } == "opens pkg to M1;")
    runCommand { statement.add(facade.createModuleReferenceFromText("M2", null)) }
    assert(readAction { statement.text } == "opens pkg to M1, M2;")
    runCommand { statement.lastChild.delete() }
    assert(readAction { statement.text } == "opens pkg to M1, M2")
    runCommand { statement.add(facade.createModuleReferenceFromText("M3", null)) }
    assert(readAction { statement.text } == "opens pkg to M1, M2, M3")
  }

  @Test
  fun testPackageAccessDirectiveTargetDeletion(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
    val file = configureFile("module M { exports pkg to M1, M2, M3; }")
    val statement = readAction { file.moduleDeclaration!!.exports.first() }
    val refs = readAction { statement.moduleReferences.toList() }
    val size = refs.size
    assert(size == 3)
    runCommand { refs[0].delete() }
    assert(readAction { statement.text } == "exports pkg to M2, M3;")
    runCommand { refs[2].delete() }
    assert(readAction { statement.text } == "exports pkg to M2;")
    runCommand { refs[1].delete() }
    assert(readAction { statement.text } == "exports pkg;")
  }

  @Test
  fun testReferenceQualifierDeletion(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
    val file = configureFile("class C {\n  Qualifier /*comment*/ . /*another*/ ref r;\n}")
    val ref = readAction { file.classes[0].fields[0].typeElement?.firstChild }
    assert(ref != null)
    runCommand {
      ref?.firstChild?.delete()
    }
    assert(readAction { ref?.text } == "ref")
  }

  @Test
  fun testExpressionQualifierDeletion(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
    val file = configureFile("class C {\n  Object o = qualifier /*comment*/ . /*another*/ expr;\n}")
    val expr = readAction { file.classes[0].fields[0].initializer }
    assert(expr != null)
    runCommand {
      expr?.firstChild?.delete()
    }
    assert(readAction { expr?.text } == "expr")
  }

  @Test
  fun testAddPackageStatementIntoFileWithBrokenPackage(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
    val file = configureFile("package ;")
    runCommand {
      file.setPackageName("foo")
    }
    readAction {
      PsiTestUtil.checkFileStructure(file)
      assert(myFixture.editor.document.text.startsWith("package foo;"))
    }
  }

  @Test
  fun testDeletingLoneImportAfterSemicolonLeavesPsiConsistent(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
    val file = configureFile("package p;;import javax.swing.*;")
    runCommand {
      file.importList?.importStatements?.get(0)?.delete()
    }
    readAction { PsiTestUtil.checkPsiMatchesTextIgnoringNonCode(file) }
  }

  @Test
  fun testTextBlockLiteralValue(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
    val file = configureFile("""
        
        class C {
          String invalid1 = ""${'"'}${'"'}${'"'}${'"'};
          String invalid2 = ""${'"'} ""${'"'};
          String invalid3 = ""${'"'}\n ""${'"'};
          String empty = ""${'"'}
            ""${'"'};
          String underIndented = ""${'"'}
            hello
           ""${'"'};
          String overIndented = ""${'"'}
            hello
              ""${'"'};
          String noTrailingNewLine = ""${'"'}
            <p>
              hello
            </p>""${'"'};
          String tabs = ""${'"'}
          	<p>
          		hello
          	</p>""${'"'};
          String openingSpaces = ""${'"'} 	  
    ""${'"'};
          String emptyLines = ""${'"'}
                              test
                                  
                                  
                              ""${'"'}; 
          String escapes = ""${'"'}
                           \n\t\"\'\\""${'"'};
        }
    """.trimIndent())
    val values = readAction {
      file.classes[0].fields.map {
        (it.initializer as PsiLiteralExpression).value
      }
    }
    assert(values[0] == null)
    assert(values[1] == null)
    assert(values[2] == null)
    assert(values[3] == "")
    assert(values[4] == " hello\n")
    assert(values[5] == "hello\n")
    assert(values[6] == "<p>\n  hello\n</p>")
    assert(values[7] == "<p>\n\thello\n</p>")
    assert(values[8] == "")
    assert(values[9] == "test\n\n\n")
    assert(values[10] == "\n\t\"\'\\")
  }

  @Test
  fun testIdempotenceCheckerUnderstandsTypeEquivalence(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
    readAction {
      rethrowLoggedErrorsIn {
        val immediate = JavaPsiFacade.getElementFactory(myFixture.project).createType(myFixture.findClass(String::class.java.name), PsiSubstitutor.EMPTY)
        val ref = JavaPsiFacade.getElementFactory(myFixture.project).createTypeFromText(String::class.java.name, null)
        assert(immediate == ref)
        assert(immediate is PsiImmediateClassType)
        assert(ref is PsiClassReferenceType)

        IdempotenceChecker.checkEquivalence(immediate as PsiType, ref, JavaPsiTest::class.java, null)

        DefaultLogger.disableStderrDumping(myFixture.testRootDisposable)

        var error = assertThrows(Throwable::class.java) {
          IdempotenceChecker.checkEquivalence(immediate as PsiType, PsiTypes.voidType(), JavaPsiTest::class.java, null)
        }
        while (error is TestLoggerFactory.TestLoggerAssertionError && error.cause != null) {
          error = error.cause!!
        }
        assertThat(error.message.orEmpty()).contains("Non-idempotent")
      }
    }
  }

  @Test
  fun testRecordComponents(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
    val file = configureFile("record A(String s, int x)")
    readAction {
      val clazz = file.classes[0]
      val recordHeader = clazz.recordHeader
      assert(recordHeader != null)
      val components = recordHeader!!.recordComponents
      assert(components[0].name == "s")
      assert(components[1].name == "x")
      assert(clazz.recordComponents.contentEquals(components))
    }
  }

  @Test
  fun testDeleteRecordComponent(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
    val file = configureFile("record A(String s, int x)")
    val clazz = readAction { file.classes[0] }
    runCommand {
      clazz.recordComponents[1].delete()
    }

    assert("record A(String s)" == readAction { clazz.text })
  }

  @Test
  fun testRecordComponentWithNameRecord(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
    // it is forbidden, but it should not fail
    val file = configureFile("record A(record r)")
    assert(readAction { file.classes[0].methods.size } == 1) // only constructor
  }

  @Test
  fun testAddRecordComponent(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
    val file = configureFile("record A(String s)")
    val clazz = readAction { file.classes[0] }
    val factory = JavaPsiFacade.getElementFactory(myFixture.project)
    val newComponent = readAction { factory.createRecordHeaderFromText("int i", null).recordComponents[0] }
    runCommand {
      val first = clazz.recordComponents[0]
      val header = clazz.recordHeader
      header!!.addAfter(newComponent, first)
    }

    assert("record A(String s, int i)" == readAction { clazz.text })
  }

  @Test
  fun testAddRecordComponentAfterRightParenthesis(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
    val file = configureFile("record A(String s)")
    val clazz = readAction { file.classes[0] }
    val factory = JavaPsiFacade.getElementFactory(myFixture.project)
    val newComponent = readAction { factory.createRecordHeaderFromText("int i", null).recordComponents[0] }
    runCommand {
      val header = clazz.recordHeader
      header!!.addBefore(newComponent, null)
    }

    assert("record A(String s, int i)" == readAction { clazz.text })
  }
  
  @Test
  fun testAddCommentToEnumClass(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
    val file = configureFile("public enum E {}")
    val clazz = readAction { file.classes[0] }
    runCommand {
      val docComment = JavaPsiFacade.getElementFactory(clazz.getProject()).createDocCommentFromText("/// Targets enum class")
      clazz.addBefore(docComment, clazz.getFirstChild())
    }

    assertEquals("""
      /// Targets enum class
      public enum E {}
    """.trimIndent(), readAction { clazz.text })
  }

  @Test
  fun testPermitsList(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
    val file = configureFile("class A permits B {}")
    readAction {
      val clazz = file.classes[0]
      val elements = clazz.permitsList!!.referenceElements
      assert(elements.size == 1)
      assert("B" == elements.first().referenceName)
    }
  }

  @Test
  fun testEnumWithNameSealed(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
    val file = configureFile("enum sealed {}")
    readAction {
      val clazz = file.classes[0]
      assert(!clazz.allMethods.any { it.name == "values" })
    }
  }

  @Test
  fun testInstanceOfNoPatternGetType(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
    val file = configureFile("class A{ boolean foo(Object a){ return a instanceof String;} }")
    readAction {
      val clazz = file.classes[0]
      val returnStatement = clazz.methods.first().body!!.statements.first() as PsiReturnStatement
      val instanceOfExpression = returnStatement.returnValue as PsiInstanceOfExpression
      assert(instanceOfExpression.checkType!!.text == "String")
    }
  }

  @Test
  fun testInstanceOfAnnotationAndModifiers(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
    readAction {
      val statement = PsiElementFactory.getInstance(myFixture.project)
        .createStatementFromText("a instanceof @Ann final Foo f", null) as PsiExpressionStatement
      val variable = PsiTreeUtil.findChildOfType(statement, PsiPatternVariable::class.java)
      assertNotNull(variable)
      assertTrue(variable!!.hasModifierProperty(PsiModifier.FINAL))
      val annotations = variable.annotations
      assertEquals(1, annotations.size)
      val annotation = annotations.first()
      assertEquals("Ann", annotation.nameReferenceElement?.referenceName)
    }
  }

  @Test
  fun `test snippet comment`(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
    readAction {
      val docComment = PsiElementFactory.getInstance(myFixture.project).createDocCommentFromText(
        """
  /**
   * Attributes:
   * {@snippet attr1="Value1"
   * attr2=value2
   *  :
   *    Body Line 1
   *    Body Line 2
   * }
   */
  """
      )

      val snippetDocTag = PsiTreeUtil.findChildOfType(docComment, PsiSnippetDocTag::class.java)!!
      assertSame(docComment, snippetDocTag.containingComment)
      val valueElement = snippetDocTag.valueElement!!
      val attributes = valueElement.attributeList.attributes

      assert(listOf("attr1", "attr2") == attributes.map { it.name })
      assert(listOf("\"Value1\"", "value2") == attributes.map { it.value!!.text })
      assert(listOf("    Body Line 1", "    Body Line 2", " ") == valueElement.body!!.content.map { it.text })
    }
  }

  @Test
  fun `test add tags as range`(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
    val file = configureFile(
      """/**
 * @t1
 * @t2
 */
class A {}
/**
 */
class B {}"""
    )

    val classes = readAction { file.classes }
    val sourceDocComment: PsiDocComment = readAction { classes[0].docComment!! }
    assertNotNull(sourceDocComment)

    val targetDocComment: PsiDocComment = readAction { classes[1].docComment!! }
    assertNotNull(targetDocComment)

    val tags: Array<PsiDocTag> = readAction { sourceDocComment.tags }
    runCommand {
      targetDocComment.addRange(tags[0], tags[tags.size - 1])
    }

    assertEquals("/**\n" +
                 " * @t1\n" +
                 " * @t2\n" +
                 " */", readAction { targetDocComment.text })
  }

  @Test
  fun `test record pattern`(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
    readAction {
      val expression = PsiElementFactory.getInstance(myFixture.project).createExpressionFromText(
        "o instanceof Record(int a, boolean b) r", null
      ) as PsiInstanceOfExpression

      val recordPattern = expression.pattern as PsiDeconstructionPattern
      assertEquals("r", recordPattern.patternVariable!!.name)

      val structurePattern = recordPattern.deconstructionList
      assertNotNull(structurePattern)

      val components = structurePattern.deconstructionComponents
      val pattern = components[1] as PsiTypeTestPattern
      assertEquals("b", pattern.patternVariable!!.name)
    }
  }

  @Test
  fun `test record pattern rename`(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
    readAction {
      val expression = PsiElementFactory.getInstance(myFixture.project).createExpressionFromText(
        "o instanceof Record(int a, boolean b) r", null
      ) as PsiInstanceOfExpression

      val recordPattern = expression.pattern as PsiDeconstructionPattern
      recordPattern.patternVariable!!.setName("foo")
      assertEquals("o instanceof Record(int a, boolean b) foo", expression.text)
    }
  }

  @Test
  fun `test record pattern type`(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
    readAction {
      val expression = PsiElementFactory.getInstance(myFixture.project)
        .createExpressionFromText("o instanceof Record(int a, boolean b)", null) as PsiInstanceOfExpression
      val recordPattern = expression.pattern as PsiDeconstructionPattern
      assertEquals("Record", recordPattern.typeElement.text)
    }
  }

  @Test
  fun `test foreach pattern`(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
    readAction {
      val stmt = PsiElementFactory.getInstance(myFixture.project)
        .createStatementFromText("for (Rec(int i) : recs);", null) as PsiForeachPatternStatement
      val declaration = stmt.iterationPattern as PsiDeconstructionPattern
      assertEquals("Rec", declaration.typeElement.text)
    }
  }

  @Test
  fun `test foreach parameter`(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
    readAction {
      val stmt = PsiElementFactory.getInstance(myFixture.project)
        .createStatementFromText("for (int i : recs);", null) as PsiForeachStatement
      val parameter = stmt.iterationParameter
      assertEquals("int", parameter.typeElement!!.text)
    }
  }

  @Test
  fun testImplicitFile(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
    val file = configureFile(
      """
        void myMethod() {
          
        }
        
        class A {
        
        }
        
        int field;
      """.trimIndent())
    readAction {
      val implicitClass = file.classes.single() as PsiImplicitClass
      val method = implicitClass.methods.single()
      assertEquals("myMethod", method.name)
      val aClass = implicitClass.innerClasses.single()
      assertEquals("A", aClass.name)
      val field = implicitClass.fields.single()
      assertEquals("field", field.name)
      assertEquals(CommonClassNames.JAVA_LANG_OBJECT, implicitClass.superClass!!.qualifiedName)
      assertEquals("Object", implicitClass.superClassType!!.name)
      InheritanceUtil.isInheritor(implicitClass, CommonClassNames.JAVA_LANG_OBJECT)
    }
  }

  private fun configureFile(@Language("JAVA") text: String): PsiJavaFile {
    return myFixture.configureByText("a.java", text) as PsiJavaFile
  }

  private fun runCommand(block: ThrowableRunnable<RuntimeException>) {
    WriteCommandAction.writeCommandAction(myFixture.project).run(block)
  }
}
