/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.java.propertyBased

import com.intellij.lang.java.lexer.JavaLexer
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.pom.java.LanguageLevel
import com.intellij.psi.*
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.testFramework.IdeaTestUtil
import com.intellij.testFramework.SkipSlowTestLocally
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase
import com.intellij.testFramework.propertyBased.PsiIndexConsistencyTester
import com.intellij.testFramework.propertyBased.PsiIndexConsistencyTester.Action
import com.intellij.testFramework.propertyBased.PsiIndexConsistencyTester.Action.*
import com.intellij.testFramework.propertyBased.PsiIndexConsistencyTester.Model
import com.intellij.testFramework.propertyBased.PsiIndexConsistencyTester.RefKind
import jetCheck.Generator
import jetCheck.PropertyChecker

/**
 * @author peter
 */
@SkipSlowTestLocally
class JavaPsiIndexConsistencyTest : LightCodeInsightFixtureTestCase() {

  fun testFuzzActions() {
    val genAction: Generator<Action> = Generator.anyOf(
      PsiIndexConsistencyTester.commonActions(PsiIndexConsistencyTester.commonRefs + listOf(ClassRef)),
      Generator.sampledFrom(AddImport, AddEnum, InvisiblePsiChange),
      Generator.booleans().map { ChangeLanguageLevel(if (it) LanguageLevel.HIGHEST else LanguageLevel.JDK_1_3) },
      Generator.from { data -> TextChange(Generator.asciiIdentifiers().suchThat { !JavaLexer.isKeyword(it, LanguageLevel.HIGHEST) }.generateValue(data),
                                          Generator.booleans().generateValue(data),
                                          Generator.booleans().generateValue(data)) }
    )
    PropertyChecker.forAll(Generator.listsOf(genAction)).withIterationCount(20).shouldHold { actions ->
      PsiIndexConsistencyTester.runActions(JavaModel(myFixture), *actions.toTypedArray())
      true
    }

  }

  internal class JavaModel(fixture: JavaCodeInsightTestFixture) : Model(fixture.addFileToProject("Foo.java", "class Foo {} ").virtualFile, fixture) {
    var vfsClassName = "Foo"
    var docClassName = "Foo"
    var psiClassName = "Foo"

    override fun onCommit() {
      super.onCommit()
      psiClassName = docClassName
    }

    override fun onReload() {
      super.onReload()
      docClassName = vfsClassName
    }

    override fun onSave() {
      super.onSave()
      vfsClassName = docClassName
    }
  }

  private object AddImport: SimpleAction() {
    override fun performAction(model: Model) {
      Commit.performAction(model)
      model.findPsiJavaFile().importList!!.add(JavaPsiFacade.getElementFactory(model.project).createImportStatementOnDemand("java.io"))
    }
  }
  private object InvisiblePsiChange: SimpleAction() {
    override fun performAction(model: Model) {
      Commit.performAction(model)
      val ref = model.refs[ClassRef] as PsiClass?
      val cls = if (ref != null && ref.isValid) ref else model.findPsiClass()
      cls.replace(cls.copy())
    }
  }

  private object AddEnum: SimpleAction() {
    override fun performAction(model: Model) {
      Commit.performAction(model)
      model.findPsiFile().add(JavaPsiFacade.getElementFactory(model.project).createEnum("SomeEnum"))
    }
  }

  private data class ChangeLanguageLevel(val level: LanguageLevel): Action {
    override fun performAction(model: Model) {
      PostponedFormatting.performAction(model)
      IdeaTestUtil.setModuleLanguageLevel(model.fixture.module, level)
    }
  }

  private data class TextChange(val newClassName: String, val viaDocument: Boolean, val withImport: Boolean): Action {
    override fun performAction(model: Model) {
      model as JavaModel
      PostponedFormatting.performAction(model)
      val counterBefore = PsiManager.getInstance(model.project).modificationTracker.javaStructureModificationCount
      model.docClassName = newClassName
      val newText = (if (withImport) "import zoo.Zoo; "  else "") + "class $newClassName { }"
      if (viaDocument) {
        model.getDocument().setText(newText)
      } else {
        Save.performAction(model)
        VfsUtil.saveText(model.vFile, newText)
        model.vfsClassName = newClassName
      }

      if (model.isCommitted()) {
        model.onCommit()
        assert(counterBefore != PsiManager.getInstance(model.project).modificationTracker.javaStructureModificationCount)
      }
    }
  }

  private object ClassRef : RefKind(){
    override fun loadRef(model: Model) = model.findPsiClass()
  }
}

private fun Model.findPsiJavaFile() = PsiManager.getInstance(project).findFile(vFile) as PsiJavaFile
private fun Model.findPsiClass() = JavaPsiFacade.getInstance(project).findClass((this as JavaPsiIndexConsistencyTest.JavaModel).psiClassName, GlobalSearchScope.allScope(project))!!
