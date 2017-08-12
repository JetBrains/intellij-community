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

import com.intellij.java.propertyBased.PsiIndexConsistencyTest.Action.*
import com.intellij.lang.java.lexer.JavaLexer
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.roots.impl.PushedFilePropertiesUpdater
import com.intellij.openapi.util.Conditions
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.pom.java.LanguageLevel
import com.intellij.psi.*
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.impl.source.PostprocessReformattingAspect
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.testFramework.IdeaTestUtil
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.SkipSlowTestLocally
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase
import com.intellij.util.FileContentUtilCore
import jetCheck.Generator
import jetCheck.PropertyChecker

/**
 * @author peter
 */
@SkipSlowTestLocally
class PsiIndexConsistencyTest: LightCodeInsightFixtureTestCase() {

  fun testFuzzActions() {
    val genAction: Generator<Action> = Generator.frequency(mapOf(
      1 to Generator.constant(Gc),
      50 to Generator.sampledFrom(Commit,
                                  AddImport,
                                  AddEnum,
                                  ReparseFile,
                                  FilePropertiesChanged,
                                  Reformat,
                                  InvisiblePsiChange,
                                  PostponedFormatting,
                                  RenamePsiFile,
                                  RenameVirtualFile,
                                  Save),
      10 to Generator.sampledFrom(*RefKind.values()).map { LoadRef(it) },
      10 to Generator.sampledFrom(*RefKind.values()).map { ClearRef(it) },
      5 to Generator.booleans().map { ChangeLanguageLevel(if (it) LanguageLevel.HIGHEST else LanguageLevel.JDK_1_3) },
      5 to Generator.from { data -> TextChange(Generator.asciiIdentifiers().suchThat { !JavaLexer.isKeyword(it, LanguageLevel.HIGHEST) }.generateValue(data),
                                               Generator.booleans().generateValue(data),
                                               Generator.booleans().generateValue(data)) }
    ))
    PropertyChecker.forAll(Generator.listsOf(genAction)).withIterationCount(20).shouldHold { actions ->
      runActions(*actions.toTypedArray())
      true
    }

  }

  private fun runActions(vararg actions: Action) {
    val vFile = myFixture.addFileToProject("Foo.java", "class Foo {} ").virtualFile
    val model = Model(vFile, myFixture)

    WriteCommandAction.runWriteCommandAction(project) {
      try {
        actions.forEach { it.performAction(model) }
      } finally {
        try {
          Save.performAction(model)
          vFile.delete(this)
        }
        catch(e: Throwable) {
          e.printStackTrace()
        }
      }
    }
  }

  internal class Model(val vFile: VirtualFile, val fixture: CodeInsightTestFixture) {
    val refs = hashMapOf<RefKind, Any?>()
    val project = fixture.project!!
    var docClassName = "Foo"
    var psiClassName = "Foo"

    fun findPsiFile() = PsiManager.getInstance(project).findFile(vFile) as PsiJavaFile
    fun findPsiClass() = JavaPsiFacade.getInstance(project).findClass(psiClassName, GlobalSearchScope.allScope(project))!!
    fun getDocument() = FileDocumentManager.getInstance().getDocument(vFile)!!
  }

  internal interface Action {

    fun performAction(model: Model)

    abstract class SimpleAction: Action {
      override fun toString(): String = javaClass.simpleName
    }

    object Gc: SimpleAction() {
      override fun performAction(model: Model) = PlatformTestUtil.tryGcSoftlyReachableObjects()
    }
    object Commit: SimpleAction() {
      override fun performAction(model: Model) {
        PsiDocumentManager.getInstance(model.project).commitAllDocuments()
        model.psiClassName = model.docClassName
      }
    }
    object Save: SimpleAction() {
      override fun performAction(model: Model) {
        PostponedFormatting.performAction(model)
        FileDocumentManager.getInstance().saveAllDocuments()
      }
    }
    object PostponedFormatting: SimpleAction() {
      override fun performAction(model: Model) =
        PostprocessReformattingAspect.getInstance(model.project).doPostponedFormatting()
    }
    object ReparseFile : SimpleAction() {
      override fun performAction(model: Model) {
        PostponedFormatting.performAction(model)
        FileContentUtilCore.reparseFiles(model.vFile)
      }
    }
    object FilePropertiesChanged : SimpleAction() {
      override fun performAction(model: Model) {
        PostponedFormatting.performAction(model)
        PushedFilePropertiesUpdater.getInstance(model.project).filePropertiesChanged(model.vFile, Conditions.alwaysTrue())
      }
    }
    object AddImport: SimpleAction() {
      override fun performAction(model: Model) {
        Commit.performAction(model)
        model.findPsiFile().importList!!.add(JavaPsiFacade.getElementFactory(model.project).createImportStatementOnDemand("java.io"))
      }
    }
    object InvisiblePsiChange: SimpleAction() {
      override fun performAction(model: Model) {
        Commit.performAction(model)
        val ref = model.refs[RefKind.ClassRef] as PsiClass?
        val cls = if (ref != null && ref.isValid) ref else model.findPsiClass()
        cls.replace(cls.copy())
      }
    }
    object RenameVirtualFile: SimpleAction() {
      override fun performAction(model: Model) {
        model.vFile.rename(this, model.vFile.nameWithoutExtension + "1.java")
      }
    }
    object RenamePsiFile: SimpleAction() {
      override fun performAction(model: Model) {
        val newName = model.vFile.nameWithoutExtension + "1.java"
        model.findPsiFile().name = newName
        assert(model.findPsiFile().name == newName)
        assert(model.vFile.name == newName)
      }
    }
    object AddEnum: SimpleAction() {
      override fun performAction(model: Model) {
        Commit.performAction(model)
        model.findPsiFile().add(JavaPsiFacade.getElementFactory(model.project).createEnum("SomeEnum"))
      }
    }
    object Reformat: SimpleAction() {
      override fun performAction(model: Model) {
        Commit.performAction(model)
        CodeStyleManager.getInstance(model.project).reformat(model.findPsiFile())
      }
    }
    data class ChangeLanguageLevel(val level: LanguageLevel): Action {
      override fun performAction(model: Model) {
        PostponedFormatting.performAction(model)
        IdeaTestUtil.setModuleLanguageLevel(model.fixture.module, level)
      }
    }
    data class LoadRef(val kind: RefKind): Action {
      override fun performAction(model: Model) {
        model.refs[kind] = kind.loadRef(model)
      }
    }
    data class ClearRef(val kind: RefKind): Action {
      override fun performAction(model: Model) {
        model.refs.remove(kind)
      }
    }
    data class TextChange(val newClassName: String, val viaDocument: Boolean, val withImport: Boolean): Action {
      override fun performAction(model: Model) {
        PostponedFormatting.performAction(model)
        val counterBefore = PsiManager.getInstance(model.project).modificationTracker.javaStructureModificationCount
        model.docClassName = newClassName
        val newText = (if (withImport) "import zoo.Zoo; "  else "") + "class ${model.docClassName} { }"
        if (viaDocument) {
          model.getDocument().setText(newText)
        } else {
          Save.performAction(model)
          VfsUtil.saveText(model.vFile, newText)
        }

        if (PsiDocumentManager.getInstance(model.project).uncommittedDocuments.isEmpty()) {
          model.psiClassName = model.docClassName
          assert(counterBefore != PsiManager.getInstance(model.project).modificationTracker.javaStructureModificationCount)
        }
      }
    }
  }

  internal enum class RefKind(val loadRef: (Model) -> Any) {
    ClassRef({ it.findPsiClass() }),
    PsiFileRef({ it.findPsiFile()}),
    AstRef({ it.findPsiClass().node }),
    DocumentRef({ it.getDocument() }),
    DirRef({ it.findPsiFile().containingDirectory })
  }
}