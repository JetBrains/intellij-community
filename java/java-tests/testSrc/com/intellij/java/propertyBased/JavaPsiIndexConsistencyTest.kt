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
import com.intellij.openapi.roots.LanguageLevelModuleExtensionImpl
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
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase
import com.intellij.testFramework.propertyBased.PsiIndexConsistencyTester
import com.intellij.testFramework.propertyBased.PsiIndexConsistencyTester.Action
import com.intellij.testFramework.propertyBased.PsiIndexConsistencyTester.Action.*
import com.intellij.testFramework.propertyBased.PsiIndexConsistencyTester.Model
import com.intellij.testFramework.propertyBased.PsiIndexConsistencyTester.RefKind
import org.jetbrains.jetCheck.Generator
import org.jetbrains.jetCheck.PropertyChecker
import org.junit.Assert

/**
 * @author peter
 */
@SkipSlowTestLocally
class JavaPsiIndexConsistencyTest : LightCodeInsightFixtureTestCase() {
  fun testFuzzActions() {
    val genAction: Generator<Action> = Generator.frequency(
      10, Generator.sampledFrom(
      PsiIndexConsistencyTester.commonActions +
      PsiIndexConsistencyTester.refActions(PsiIndexConsistencyTester.commonRefs + listOf(ClassRef)) + 
      listOf(AddImport, AddEnum, InvisiblePsiChange) + 
      listOf(true, false).map { ChangeLanguageLevel(if (it) LanguageLevel.HIGHEST else LanguageLevel.JDK_1_3) }
    ),
      1, Generator.from { data -> TextChange(data.generateConditional(Generator.asciiIdentifiers()) { !JavaLexer.isKeyword(it, LanguageLevel.HIGHEST) },
                                                   data.generate(Generator.booleans()),
                                                   data.generate(Generator.booleans())) })
    PropertyChecker.forAll(Generator.listsOf(genAction)).shouldHold { actions ->
      val prevLevel = LanguageLevelModuleExtensionImpl.getInstance(myFixture.module).languageLevel
      try {
        PsiIndexConsistencyTester.runActions(JavaModel(myFixture), *actions.toTypedArray())
      }
      finally {
        IdeaTestUtil.setModuleLanguageLevel(myFixture.module, prevLevel)
      }
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
    val newText = (if (withImport) "import zoo.Zoo; "  else "") + "class $newClassName { }"

    override fun toString(): String = "TextChange(via=${if (viaDocument) "document" else "VFS"}, text=\"$newText\")"

    override fun performAction(model: Model) {
      model as JavaModel
      PostponedFormatting.performAction(model)
      val counterBefore = PsiManager.getInstance(model.project).modificationTracker.javaStructureModificationCount
      model.docClassName = newClassName
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
