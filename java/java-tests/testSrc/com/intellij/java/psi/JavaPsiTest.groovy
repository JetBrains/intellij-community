/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.java.psi

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.PsiJavaFile
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
    runCommand { statement.add(facade.createModuleReferenceFromText("M1")) }
    assert statement.text == "opens pkg to M1;"
    runCommand { statement.add(facade.createModuleReferenceFromText("M2")) }
    assert statement.text == "opens pkg to M1, M2;"
    runCommand { statement.lastChild.delete() }
    assert statement.text == "opens pkg to M1, M2"
    runCommand { statement.add(facade.createModuleReferenceFromText("M3")) }
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

  private PsiJavaFile configureFile(String text) {
    myFixture.configureByText("a.java", text) as PsiJavaFile
  }

  private void runCommand(ThrowableRunnable block) {
    WriteCommandAction.writeCommandAction(project).run(block)
  }
}