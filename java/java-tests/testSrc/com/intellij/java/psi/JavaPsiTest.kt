// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.psi

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.diagnostic.DefaultLogger
import com.intellij.psi.*
import com.intellij.psi.impl.source.PsiClassReferenceType
import com.intellij.psi.impl.source.PsiImmediateClassType
import com.intellij.psi.javadoc.PsiDocComment
import com.intellij.psi.javadoc.PsiDocTag
import com.intellij.psi.javadoc.PsiSnippetDocTag
import com.intellij.psi.util.InheritanceUtil
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import com.intellij.util.IdempotenceChecker
import com.intellij.util.ThrowableRunnable
import junit.framework.TestCase
import org.intellij.lang.annotations.Language

class JavaPsiTest : LightJavaCodeInsightFixtureTestCase() {
  fun testEmptyImportList() {
    assert(configureFile("").importList != null)
    assert(configureFile("class C { }").importList != null)
    assert(configureFile("module M { }").importList != null)
  }

  fun testModuleInfo() {
    val file = configureFile("module M { }")
    assert(file.packageName == "")
    val module = file.moduleDeclaration
    require(module != null)
    assert(module.name == "M")
    assert(module.modifierList != null)
  }

  fun testInstanceOf() {
    val file = configureFile("class A { void foo(A a) { a instanceof B x; } }")
    val expression = (file.classes[0].methods[0].body!!.statements[0] as PsiExpressionStatement).expression as PsiInstanceOfExpression
    val pattern = expression.pattern
    assert(pattern != null)
    val typeTestPattern = pattern as PsiTypeTestPattern
    val variable = pattern.patternVariable
    require(variable != null)
    assert(variable.name == "x")
    assert(typeTestPattern.checkType!!.text == "B")
    WriteCommandAction.runWriteCommandAction(project) {
      variable.setName("bar")
      assert(expression.text == "a instanceof B bar")
    }
  }

  fun testPackageAccessDirectiveTargetInsertion() {
    val file = configureFile("module M { opens pkg; }")
    val statement = file.moduleDeclaration!!.opens.first()
    val facade = myFixture.javaFacade.parserFacade
    runCommand { statement.add(facade.createModuleReferenceFromText("M1", null)) }
    assert(statement.text == "opens pkg to M1;")
    runCommand { statement.add(facade.createModuleReferenceFromText("M2", null)) }
    assert(statement.text == "opens pkg to M1, M2;")
    runCommand { statement.lastChild.delete() }
    assert(statement.text == "opens pkg to M1, M2")
    runCommand { statement.add(facade.createModuleReferenceFromText("M3", null)) }
    assert(statement.text == "opens pkg to M1, M2, M3")
  }

  fun testPackageAccessDirectiveTargetDeletion() {
    val file = configureFile("module M { exports pkg to M1, M2, M3; }")
    val statement = file.moduleDeclaration!!.exports.first()
    val refs = statement.moduleReferences.toList()
    val size = refs.size
    assert(size == 3)
    runCommand { refs[0].delete() }
    assert(statement.text == "exports pkg to M2, M3;")
    runCommand { refs[2].delete() }
    assert(statement.text == "exports pkg to M2;")
    runCommand { refs[1].delete() }
    assert(statement.text == "exports pkg;")
  }


  fun testReferenceQualifierDeletion() {
    val file = configureFile("class C {\n  Qualifier /*comment*/ . /*another*/ ref r;\n}")
    val ref = file.classes[0].fields[0].typeElement?.firstChild
    assert(ref != null)
    runCommand {
      ref?.firstChild?.delete()
    }
    assert(ref?.text == "ref")
  }

  fun testExpressionQualifierDeletion() {
    val file = configureFile("class C {\n  Object o = qualifier /*comment*/ . /*another*/ expr;\n}")
    val expr = file.classes[0].fields[0].initializer
    assert(expr != null)
    runCommand {
      expr?.firstChild?.delete()
    }
    assert(expr?.text == "expr")
  }

  fun testAddPackageStatementIntoFileWithBrokenPackage() {
    val file = configureFile("package ;")
    runCommand {
      file.setPackageName("foo")
    }
    PsiTestUtil.checkFileStructure(file)
    assert(myFixture.editor.document.text.startsWith("package foo;"))
  }

  fun testDeletingLoneImportAfterSemicolonLeavesPsiConsistent() {
    val file = configureFile("package p;;import javax.swing.*;")
    runCommand {
      file.importList?.importStatements?.get(0)?.delete()
    }
    PsiTestUtil.checkPsiMatchesTextIgnoringNonCode(file)
  }

  fun testTextBlockLiteralValue() {
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
    val values = file.classes[0].fields.map {
      (it.initializer as PsiLiteralExpression).value
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

  fun testIdempotenceCheckerUnderstandsTypeEquivalence() {
    val immediate = elementFactory.createType(myFixture.findClass(String::class.java.name), PsiSubstitutor.EMPTY)
    val ref = elementFactory.createTypeFromText(String::class.java.name, null)
    assert(immediate == ref)
    assert(immediate is PsiImmediateClassType)
    assert(ref is PsiClassReferenceType)

    IdempotenceChecker.checkEquivalence(immediate as PsiType, ref, this::class.java, null)

    DefaultLogger.disableStderrDumping(testRootDisposable)

    assertThrows(Throwable::class.java, "Non-idempotent") {
      IdempotenceChecker.checkEquivalence(immediate as PsiType, PsiTypes.voidType(), this::class.java, null)
    }
  }

  fun testRecordComponents() {
    val clazz = configureFile("record A(String s, int x)").classes[0]
    val recordHeader = clazz.recordHeader
    assert(recordHeader != null)
    val components = recordHeader!!.recordComponents
    assert(components[0].name == "s")
    assert(components[1].name == "x")
    assert(clazz.recordComponents.contentEquals(components))
  }

  fun testDeleteRecordComponent() {
    val clazz = configureFile("record A(String s, int x)").classes[0]
    runCommand {
      clazz.recordComponents[1].delete()
    }

    assert("record A(String s)" == clazz.text)
  }

  fun testRecordComponentWithNameRecord() {
    // it is forbidden, but it should not fail
    val clazz = configureFile("record A(record r)").classes[0]
    assert(clazz.methods.size == 1) // only constructor
  }

  fun testAddRecordComponent() {
    val clazz = configureFile("record A(String s)").classes[0]
    val factory = JavaPsiFacade.getElementFactory(project)
    val newComponent = factory.createRecordHeaderFromText("int i", null).recordComponents[0]
    runCommand {
      val first = clazz.recordComponents[0]
      val header = clazz.recordHeader
      header!!.addAfter(newComponent, first)
    }

    assert("record A(String s, int i)" == clazz.text)
  }

  fun testAddRecordComponentAfterRightParenthesis() {
    val clazz = configureFile("record A(String s)").classes[0]
    val factory = JavaPsiFacade.getElementFactory(project)
    val newComponent = factory.createRecordHeaderFromText("int i", null).recordComponents[0]
    runCommand {
      val header = clazz.recordHeader
      header!!.addBefore(newComponent, null)
    }

    assert("record A(String s, int i)" == clazz.text)
  }

  fun testPermitsList() {
    val clazz = configureFile("class A permits B {}").classes[0]
    val elements = clazz.permitsList!!.referenceElements
    assert(elements.size == 1)
    assert("B" == elements.first().referenceName)
  }

  fun testEnumWithNameSealed() {
    val clazz = configureFile("enum sealed {}").classes[0]
    assert(!clazz.allMethods.any { it.name == "values" })
  }

  fun testInstanceOfNoPatternGetType() {
    val clazz = configureFile("class A{ boolean foo(Object a){ return a instanceof String;} }").classes[0]
    val returnStatement = clazz.methods.first().body!!.statements.first() as PsiReturnStatement
    val instanceOfExpression = returnStatement.returnValue as PsiInstanceOfExpression
    assert(instanceOfExpression.checkType!!.text == "String")
  }

  fun testInstanceOfAnnotationAndModifiers() {
    val statement = PsiElementFactory.getInstance(project)
      .createStatementFromText("a instanceof @Ann final Foo f", null) as PsiExpressionStatement
    val variable = PsiTreeUtil.findChildOfType(statement, PsiPatternVariable::class.java)
    assertNotNull(variable)
    assertTrue(variable!!.hasModifierProperty(PsiModifier.FINAL))
    val annotations = variable.annotations
    assertEquals(1, annotations.size)
    val annotation = annotations.first()
    assertEquals("Ann", annotation.nameReferenceElement?.referenceName)
  }

  fun `test snippet comment`() {
    val docComment = PsiElementFactory.getInstance(project).createDocCommentFromText(
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

    val comment = PsiTreeUtil.findChildOfType(docComment, PsiSnippetDocTag::class.java)
    val valueElement = comment!!.valueElement!!
    val attributes = valueElement.attributeList.attributes

    assert(listOf("attr1", "attr2") == attributes.map { it.name })
    assert(listOf("\"Value1\"", "value2") == attributes.map { it.value!!.text })
    assert(listOf("    Body Line 1", "    Body Line 2", " ") == valueElement.body!!.content.map { it.text })
  }

  fun `test add tags as range`() {
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

    val classes = file.classes
    val sourceDocComment: PsiDocComment = classes[0].docComment!!
    assertNotNull(sourceDocComment)

    val targetDocComment: PsiDocComment = classes[1].docComment!!
    assertNotNull(targetDocComment)

    val tags: Array<PsiDocTag> = sourceDocComment.tags
    runCommand {
      targetDocComment.addRange(tags[0], tags[tags.size - 1])
    }

    assertEquals("/**\n" +
                 " * @t1\n" +
                 " * @t2\n" +
                 " */", targetDocComment.text)
  }

  fun `test record pattern`() {
    val expression = PsiElementFactory.getInstance(project).createExpressionFromText(
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

  fun `test record pattern rename`() {
    val expression = PsiElementFactory.getInstance(project).createExpressionFromText(
      "o instanceof Record(int a, boolean b) r", null
    ) as PsiInstanceOfExpression

    val recordPattern = expression.pattern as PsiDeconstructionPattern
    recordPattern.patternVariable!!.setName("foo")
    assertEquals("o instanceof Record(int a, boolean b) foo", expression.text)
  }

  fun `test record pattern type`() {
    val expression = PsiElementFactory.getInstance(project)
      .createExpressionFromText("o instanceof Record(int a, boolean b)", null) as PsiInstanceOfExpression
    val recordPattern = expression.pattern as PsiDeconstructionPattern
    assertEquals("Record", recordPattern.typeElement.text)
  }

  fun `test foreach pattern`() {
    val stmt = PsiElementFactory.getInstance(project)
      .createStatementFromText("for (Rec(int i) : recs);", null) as PsiForeachPatternStatement
    val declaration = stmt.iterationPattern as PsiDeconstructionPattern
    assertEquals("Rec", declaration.typeElement.text)
  }

  fun `test foreach parameter`() {
    val stmt = PsiElementFactory.getInstance(project)
      .createStatementFromText("for (int i : recs);", null) as PsiForeachStatement
    val parameter = stmt.iterationParameter
    assertEquals("int", parameter.typeElement!!.text)
  }

  fun testImplicitFile() {
    val file = configureFile(
      """
        void myMethod() {
          
        }
        
        class A {
        
        }
        
        int field;
      """.trimIndent())
    val implicitClass = file.classes.single() as PsiImplicitClass
    TestCase.assertNull(implicitClass.name)
    val method = implicitClass.methods.single()
    TestCase.assertEquals("myMethod", method.name)
    val aClass = implicitClass.innerClasses.single()
    TestCase.assertEquals("A", aClass.name)
    val field = implicitClass.fields.single()
    TestCase.assertEquals("field", field.name)
    TestCase.assertEquals(CommonClassNames.JAVA_LANG_OBJECT, implicitClass.superClass!!.qualifiedName)
    TestCase.assertEquals("Object", implicitClass.superClassType!!.name)
    InheritanceUtil.isInheritor(implicitClass, CommonClassNames.JAVA_LANG_OBJECT)
  }

  private fun configureFile(@Language("JAVA") text: String): PsiJavaFile {
    return myFixture.configureByText("a.java", text) as PsiJavaFile
  }

  private fun runCommand(block: ThrowableRunnable<RuntimeException>) {
    WriteCommandAction.writeCommandAction(project).run(block)
  }
}