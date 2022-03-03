// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.psi

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.diagnostic.DefaultLogger
import com.intellij.openapi.roots.LanguageLevelProjectExtension
import com.intellij.pom.java.LanguageLevel
import com.intellij.psi.*
import com.intellij.psi.impl.source.PsiClassReferenceType
import com.intellij.psi.impl.source.PsiImmediateClassType
import com.intellij.psi.javadoc.PsiSnippetDocTag
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import com.intellij.util.IdempotenceChecker
import com.intellij.util.ThrowableRunnable
import groovy.transform.CompileStatic

@CompileStatic
class JavaPsiTest extends LightJavaCodeInsightFixtureTestCase {
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_16
  }

  void testEmptyImportList() {
    assert configureFile("").importList != null
    assert configureFile("class C { }").importList != null
    assert configureFile("module M { }").importList != null
  }

  void testModuleInfo() {
    def file = configureFile("module M { }")
    assert file.packageName == ""
    def module = file.moduleDeclaration
    assert module != null
    assert module.name == "M"
    assert module.modifierList != null
  }

  void testInstanceOf() {
    def file = configureFile("class A { void foo(A a) { a instanceof B x; } }")
    def expression = (file.classes[0].methods[0].body.statements[0] as PsiExpressionStatement).expression as PsiInstanceOfExpression
    def pattern = expression.pattern
    assert pattern instanceof PsiTypeTestPattern
    PsiTypeTestPattern typeTestPattern = pattern as PsiTypeTestPattern
    def variable = pattern.getPatternVariable()
    assert variable != null
    assert variable.name == "x"
    assert typeTestPattern.checkType.text == "B"
    WriteCommandAction.runWriteCommandAction(project, {
      variable.setName("bar")
      assert expression.text == "a instanceof B bar"
    })
  }

  void testPackageAccessDirectiveTargetInsertion() {
    def file = configureFile("module M { opens pkg; }")
    def statement = file.moduleDeclaration.opens.first()
    def facade = myFixture.javaFacade.parserFacade
    runCommand { statement.add(facade.createModuleReferenceFromText("M1", null)) }
    assert statement.text == "opens pkg to M1;"
    runCommand { statement.add(facade.createModuleReferenceFromText("M2", null)) }
    assert statement.text == "opens pkg to M1, M2;"
    runCommand { statement.lastChild.delete() }
    assert statement.text == "opens pkg to M1, M2"
    runCommand { statement.add(facade.createModuleReferenceFromText("M3", null)) }
    assert statement.text == "opens pkg to M1, M2, M3"
  }

  void testPackageAccessDirectiveTargetDeletion() {
    def file = configureFile("module M { exports pkg to M1, M2, M3; }")
    def statement = file.moduleDeclaration.exports.first()
    def refs = statement.moduleReferences.toList()
    assert refs.size() == 3
    runCommand { refs[0].delete() }
    assert statement.text == "exports pkg to M2, M3;"
    runCommand { refs[2].delete() }
    assert statement.text == "exports pkg to M2;"
    runCommand { refs[1].delete() }
    assert statement.text == "exports pkg;"
  }

  void testReferenceQualifierDeletion() {
    def file = configureFile("class C {\n  Qualifier /*comment*/ . /*another*/ ref r;\n}")
    def ref = file.classes[0].fields[0].typeElement.firstChild
    assert ref != null
    runCommand { ref.firstChild.delete() }
    assert ref.text == "ref"
  }

  void testExpressionQualifierDeletion() {
    def file = configureFile("class C {\n  Object o = qualifier /*comment*/ . /*another*/ expr;\n}")
    def expr = file.classes[0].fields[0].initializer
    assert expr != null
    runCommand { expr.firstChild.delete() }
    assert expr.text == "expr"
  }

  void "test add package statement into file with broken package"() {
    def file = configureFile("package ;")
    runCommand { file.setPackageName('foo') }
    PsiTestUtil.checkFileStructure(file)
    assert myFixture.editor.document.text.startsWith('package foo;')
  }

  void "test deleting lone import after semicolon leaves PSI consistent"() {
    def file = configureFile("package p;;import javax.swing.*;")
    runCommand { file.importList.importStatements[0].delete() }
    PsiTestUtil.checkPsiMatchesTextIgnoringNonCode(file)
  }

  void testTextBlockLiteralValue() {
    def file = configureFile("""
        class C {
          String invalid1 = \"""\""";
          String invalid2 = \""" \""";
          String invalid3 = \"""\\n \""";
          String empty = \"""
            \""";
          String underIndented = \"""
            hello
           \""";
          String overIndented = \"""
            hello
              \""";
          String noTrailingNewLine = \"""
            <p>
              hello
            </p>\""";
          String tabs = \"""
          \t<p>
          \t\thello
          \t</p>\""";
          String openingSpaces = \""" \t \f \n    \""";
          String emptyLines = \"""
                              test
                                  
                                  
                              \"""; 
          String escapes = \"""
                           \\n\\t\\"\\'\\\\\""";
        }""".stripIndent())
    def values = file.classes[0].fields.collect { ((it as PsiField).initializer as PsiLiteralExpression).value }
    assert values[0] == null
    assert values[1] == null
    assert values[2] == null
    assert values[3] == ""
    assert values[4] == " hello\n"
    assert values[5] == "hello\n"
    assert values[6] == "<p>\n  hello\n</p>"
    assert values[7] == "<p>\n\thello\n</p>"
    assert values[8] == ""
    assert values[9] == "test\n\n\n"
    assert values[10] == "\n\t\"\'\\"
  }

  void "test IdempotenceChecker understands type equivalence"() {
    def immediate = getElementFactory().createType(myFixture.findClass(String.name), PsiSubstitutor.EMPTY)
    def ref = getElementFactory().createTypeFromText(String.name, null)
    assert immediate == ref
    assert immediate instanceof PsiImmediateClassType
    assert ref instanceof PsiClassReferenceType

    IdempotenceChecker.checkEquivalence((PsiType)immediate, (PsiType)ref, getClass(), null) // shouldn't throw

    DefaultLogger.disableStderrDumping(testRootDisposable)
    assertThrows(Throwable, "Non-idempotent") {
      IdempotenceChecker.checkEquivalence((PsiType)immediate, PsiType.VOID, getClass(), null)
    }
  }

  void "test record components"() {
    def clazz = configureFile("record A(String s, int x)").classes[0]
    def recordHeader = clazz.recordHeader
    assert recordHeader != null
    def components = recordHeader.recordComponents
    assert components[0].name == "s"
    assert components[1].name == "x"
    assert clazz.recordComponents == components
  }

  void "test delete record component"() {
    def clazz = configureFile("record A(String s, int x)").classes[0]
    runCommand {
      clazz.recordComponents[1].delete()
    }

    assert "record A(String s)" == clazz.text
  }

  void "test record component with name record"() {
    // it is forbidden, but it should not fail
    def clazz = configureFile("record A(record r)").classes[0]
    assert 1 == clazz.methods.size() // only constructor
  }

  void "test add record component"() {
    def clazz = configureFile("record A(String s)").classes[0]
    def factory = JavaPsiFacade.getElementFactory(project)
    def newComponent = factory.createRecordHeaderFromText("int i", null).recordComponents[0]
    runCommand {
      def first = clazz.recordComponents[0]
      def header = clazz.recordHeader
      header.addAfter(newComponent, first)
    }

    assert "record A(String s, int i)" == clazz.text
  }

  void "test add record component after right parenthesis"() {
    def clazz = configureFile("record A(String s)").classes[0]
    def factory = JavaPsiFacade.getElementFactory(project)
    def newComponent = factory.createRecordHeaderFromText("int i", null).recordComponents[0]
    runCommand {
      def header = clazz.recordHeader
      header.addBefore(newComponent, null)
    }

    assert "record A(String s, int i)" == clazz.text
  }

  void "test permits list"() {
    def clazz = configureFile("class A permits B {}").classes[0]
    def elements = clazz.permitsList.referenceElements
    assert 1 == elements.size()
    assert "B" == elements.first().referenceName
  }

  void "test enum with name sealed"() {
    withLanguageLevel(LanguageLevel.JDK_16_PREVIEW) {
      def clazz = configureFile("enum sealed {}").classes[0]
      assert !clazz.getAllMethods().any { it.name == "values" }
    }
  }

  void "test instanceof no pattern get type"() {
    withLanguageLevel(LanguageLevel.JDK_X) {
      def clazz = configureFile("class A{ boolean foo(Object a){ return a instanceof String;} }").classes[0]
      def returnStatement = clazz.methods.first().getBody().statements.first() as PsiReturnStatement
      def instanceOfExpression = returnStatement.returnValue as PsiInstanceOfExpression
      assert instanceOfExpression.getCheckType().getText() == "String"
    }
  }

  void "test instanceof annotation and modifiers"() {
    withLanguageLevel(LanguageLevel.JDK_16_PREVIEW) {
      def statement = (PsiExpressionStatement)PsiElementFactory.getInstance(project)
        .createStatementFromText("a instanceof @Ann final Foo f", null)
      def variable = PsiTreeUtil.findChildOfType(statement, PsiPatternVariable.class)
      assertNotNull(variable)
      assertTrue(variable.hasModifierProperty(PsiModifier.FINAL))
      def annotations = variable.getAnnotations()
      assertEquals(1, annotations.size())
      def annotation = annotations.first()
      assertEquals("Ann", annotation.nameReferenceElement.referenceName)
    }
  }

  void "test snippet comment"() {
    def docComment = PsiElementFactory.getInstance(project).createDocCommentFromText("""
/**
 * Attributes:
 * {@snippet attr1="Value1"
 * attr2=value2
 *  :
 *    Body Line 1
 *    Body Line 2
 * }
 */
""")
    def comment = PsiTreeUtil.findChildOfType(docComment, PsiSnippetDocTag.class)
    def valueElement = comment.valueElement
    def attributes = valueElement.attributeList.attributes
    assert ["attr1", "attr2"] == attributes.collect { it.name }
    assert ['"Value1"', "value2"] == attributes.collect { it.value.text }
    assert ['    Body Line 1', '    Body Line 2', ' '] == valueElement.body.content.collect { it.text }
  }

  private void withLanguageLevel(LanguageLevel level, Runnable r) {
    def old = LanguageLevelProjectExtension.getInstance(getProject()).getLanguageLevel()
    LanguageLevelProjectExtension.getInstance(getProject()).setLanguageLevel(level)
    try {
      r.run()
    }
    finally {
      LanguageLevelProjectExtension.getInstance(getProject()).setLanguageLevel(old)
    }
  }

  private PsiJavaFile configureFile(String text) {
    myFixture.configureByText("a.java", text) as PsiJavaFile
  }

  private void runCommand(ThrowableRunnable block) {
    WriteCommandAction.writeCommandAction(project).run(block)
  }
}