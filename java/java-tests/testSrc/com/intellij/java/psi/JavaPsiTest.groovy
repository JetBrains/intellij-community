// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.psi

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.PsiField
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiLiteralExpression
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase
import com.intellij.util.ThrowableRunnable
import groovy.transform.CompileStatic

@CompileStatic
class JavaPsiTest extends LightCodeInsightFixtureTestCase {
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
  }

  private PsiJavaFile configureFile(String text) {
    myFixture.configureByText("a.java", text) as PsiJavaFile
  }

  private void runCommand(ThrowableRunnable block) {
    WriteCommandAction.writeCommandAction(project).run(block)
  }
}