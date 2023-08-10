// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.index.propertyBased

import com.intellij.lang.java.lexer.JavaLexer
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.pom.java.LanguageLevel
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.testFramework.IdeaTestUtil
import com.intellij.testFramework.SkipSlowTestLocally
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import com.intellij.testFramework.propertyBased.PsiIndexConsistencyTester
import com.intellij.testFramework.propertyBased.PsiIndexConsistencyTester.Action
import com.intellij.testFramework.propertyBased.PsiIndexConsistencyTester.Action.*
import com.intellij.testFramework.propertyBased.PsiIndexConsistencyTester.Model
import com.intellij.testFramework.propertyBased.PsiIndexConsistencyTester.RefKind
import org.jetbrains.jetCheck.Generator
import org.jetbrains.jetCheck.PropertyChecker
import org.junit.Assert

@SkipSlowTestLocally
class JavaPsiIndexConsistencyTest : LightJavaCodeInsightFixtureTestCase() {

  fun testFuzzActions() {
    val genAction: Generator<Action> = Generator.frequency(
      10, Generator.sampledFrom(
      PsiIndexConsistencyTester.commonActions +
      PsiIndexConsistencyTester.refActions(PsiIndexConsistencyTester.commonRefs + listOf(ClassRef)) +
      listOf(AddImport, AddEnum, InvisiblePsiChange) +
      listOf(true, false).map { ChangeLanguageLevel(if (it) LanguageLevel.HIGHEST else LanguageLevel.JDK_1_3) }
    ),
      1, Generator.from { data -> JavaTextChange(data.generate(Generator.asciiIdentifiers().suchThat { !JavaLexer.isKeyword(it, LanguageLevel.HIGHEST) }),
                                                 data.generate(Generator.booleans()),
                                                 data.generate(Generator.booleans())) })
    PropertyChecker.customized().forAll(Generator.listsOf(genAction)) { actions ->
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
      IdeaTestUtil.setModuleLanguageLevel(model.fixture.module, level, model.fixture.testRootDisposable)
      model.refs.clear()
    }
  }

  open class TextChange(val text: String, val viaDocument: Boolean): Action {
    override fun toString(): String = "TextChange(via=${if (viaDocument) "document" else "VFS"}, text=\"$text\")"

    override fun performAction(model: Model) {
      PostponedFormatting.performAction(model)
      if (viaDocument) {
        model.getDocument().setText(text)
      } else {
        Save.performAction(model)
        VfsUtil.saveText(model.vFile, text)
      }
    }
  }

  private class JavaTextChange(val newClassName: String, viaDocument: Boolean, withImport: Boolean):
    TextChange((if (withImport) "import zoo.Zoo; "  else "") + "class $newClassName { }", viaDocument) {

    override fun performAction(model: Model) {
      val counterBefore = PsiManager.getInstance(model.project).modificationTracker.modificationCount
      super.performAction(model)
      model as JavaModel
      model.docClassName = newClassName
      if (!viaDocument) {
        model.vfsClassName = newClassName
      }

      if (model.isCommitted()) {
        model.onCommit()
        assert(counterBefore != PsiManager.getInstance(model.project).modificationTracker.modificationCount)
      }
    }
  }

  private object ClassRef : RefKind(){
    override fun loadRef(model: Model) = model.findPsiClass()
    override fun checkDuplicates(oldValue: Any, newValue: Any) {
      oldValue as PsiClass
      newValue as PsiClass
      if (oldValue.isValid && (newValue.containingFile as PsiJavaFile).classes.size == 1) {
        // if there are >1 classes in the file, it could be that after reparse previously retrieved PsiClass instance is now pointing to a non-first one, and so there's no duplicate 
        Assert.fail("Duplicate PSI elements: $oldValue and $newValue")
      }
    }
  }
}

private fun Model.findPsiJavaFile() = PsiManager.getInstance(project).findFile(vFile) as PsiJavaFile
private fun Model.findPsiClass(): PsiClass {
  val name = (this as JavaPsiIndexConsistencyTest.JavaModel).psiClassName
  return JavaPsiFacade.getInstance(project).findClass(name, GlobalSearchScope.allScope(project)) ?: 
              error("Expected to find class named \"$name\"")
}
