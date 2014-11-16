/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.index

import com.intellij.util.containers.ContainerUtil
import groovy.lang.GroovyClassLoader
import junit.framework.{TestResult, TestCase}
import org.scalacheck.Arbitrary.arbitrary
import org.scalacheck.Gen._
import org.scalacheck.Prop.forAll
import org.scalacheck._
import scala.collection.JavaConverters._

/**
 * Run this class to generate randomized tests for IDEA VFS/document/PSI/index subsystem interaction using ScalaCheck.
 * When a test fails, a test method code (in Groovy) is printed which should be copied to a normal test class and debugged there.
 * The generated test may contain some excessive declarations and checks that should be corrected manually.
 * After the fix, that generated test should be renamed according to the underlying issue it found and committed to the repository.
 *
 * @author peter
 */
object IndexTestGenerator {
  val genAction: Gen[Action] = oneOf(
    const(Gc),
    const(Commit),
    const(Save),
    for (withImport <- arbitrary[Boolean]; viaDocument <- arbitrary[Boolean]) yield TextChange(viaDocument, withImport),
    for (load <- arbitrary[Boolean]) yield UpdatePsiClassRef(load),
    for (load <- arbitrary[Boolean]) yield UpdatePsiFileRef(load),
    for (load <- arbitrary[Boolean]) yield UpdateASTNodeRef(load),
    for (load <- arbitrary[Boolean]) yield UpdateDocumentRef(load)
  )
  val propIndexTest = forAll(Gen.nonEmptyListOf(genAction)) { actions =>
    new IndexTestSeq(actions).isSuccessful
  }

  def main(args: Array[String]) {
    propIndexTest.check
  }
}

case class IndexTestSeq(actions: List[Action]) {
  def printClass: String = {
    val sb = StringBuilder.newBuilder
    sb.append(prefix)
    sb.append("\n" +
      "public void \"" + testName + "\"() {\n" +
      "def vFile =\n  myFixture.addFileToProject(\"Foo.java\", \"class Foo {}\").virtualFile\n" +
      "def lastPsiName = \"Foo\"\n" +
      "long counterBefore\n" +
      "Document document\n" +
      "PsiFile psiFile\n" +
      "ASTNode astNode\n" +
      "PsiClass psiClass\n" +
      "def scope = GlobalSearchScope.allScope(project)\n")
    var changeId = 0
    var docClassName = "Foo"
    for (action <- actions) {
      sb.append("\n")
      action match {
        case Gc =>
          sb.append("PlatformTestUtil.tryGcSoftlyReachableObjects()\n")
        case Commit =>
          sb.append("PsiDocumentManager.getInstance(project).commitAllDocuments()\nlastPsiName = \"" + docClassName + "\"\n")
        case Save =>
          sb.append("FileDocumentManager.instance.saveAllDocuments()\n")
        case UpdatePsiClassRef(load) =>
          sb.append("psiClass = " + (if (load) "JavaPsiFacade.getInstance(project).findClass(lastPsiName, scope)" else "null") + "\n")
          if (load) sb.append("assert psiClass\n")
        case UpdatePsiFileRef(load) =>
          sb.append("psiFile = " + (if (load) "psiManager.findFile(vFile)" else "null") + "\n")
        case UpdateASTNodeRef(load) =>
          sb.append("astNode = " + (if (load) "JavaPsiFacade.getInstance(project).findClass(lastPsiName, scope).node" else "null") + "\n")
        case UpdateDocumentRef(load) =>
          sb.append("document = " + (if (load) "FileDocumentManager.instance.getDocument(vFile)" else "null") + "\n")
        case TextChange(viaDocument, withImport) =>
          changeId += 1
          docClassName = "Foo" + changeId
          val newText = (if (withImport) "import zoo.Zoo; "  else "") + "class " + docClassName + " {}"

          sb.append("counterBefore =\n  psiManager.modificationTracker.javaStructureModificationCount\n")

          if (viaDocument) {
            sb.append("FileDocumentManager.instance.getDocument(vFile).text =\n  \"" + newText + "\"\n")
          } else {
            sb.append("//todo remove if statement or replace with its content \n")
            sb.append("if (FileDocumentManager.instance.unsavedDocuments) {\n  FileDocumentManager.instance.saveAllDocuments()\n}\n")
            sb.append("VfsUtil.saveText(vFile, \"" + newText + "\")\n")
          }

          sb.append(
            "// todo replace if statement with assertions\n" +
            "if (!PsiDocumentManager.getInstance(project).uncommittedDocuments) {\n" +
            "  lastPsiName = \"" + docClassName + "\"\n" +
            "  assert counterBefore !=\n    psiManager.modificationTracker.javaStructureModificationCount\n" +
            "}\n")
      }
    }
    sb.append("}\n}")
    sb.toString()
  }

  val prefix = "import com.intellij.lang.ASTNode\n" +
    "import com.intellij.openapi.command.WriteCommandAction\n" +
    "import com.intellij.openapi.editor.Document\n" +
    "import com.intellij.openapi.fileEditor.FileDocumentManager\n" +
    "import com.intellij.openapi.util.Ref\n" +
    "import com.intellij.openapi.vfs.VfsUtil\n" +
    "import com.intellij.psi.*\n" +
    "import com.intellij.psi.search.GlobalSearchScope\n" +
    "import com.intellij.testFramework.PlatformTestUtil\n" +
    "import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase\n" +
    "import com.intellij.util.ObjectUtils\n" +
    "import org.jetbrains.annotations.NotNull\n" +
    "class DummyTest extends JavaCodeInsightFixtureTestCase {\n" +
    "protected void invokeTestRunnable(Runnable runnable) {\n" +
    "  WriteCommandAction.runWriteCommandAction(project, runnable)\n" +
    "}\n"

  val testName: String = "test please write a meaningful description here"

  def isSuccessful: Boolean = {
    println(actions)

    val classText = printClass
    val test = new GroovyClassLoader().parseClass(classText).newInstance().asInstanceOf[TestCase]
    test.setName(testName)
    val result: TestResult = test.run()
    for (failure <- ContainerUtil.toList(result.failures()).asScala) {
      println (failure.trace())
    }
    for (failure <- ContainerUtil.toList(result.errors()).asScala) {
      println (failure.trace())
    }
    if (!result.wasSuccessful()) {
      println(classText)
    }
    result.wasSuccessful()
  }

}

class Action
case object Gc extends Action
case object Commit extends Action
case object Save extends Action
case class TextChange(viaDocument: Boolean, withImport: Boolean) extends Action
case class UpdatePsiClassRef(load: Boolean) extends Action
case class UpdatePsiFileRef(load: Boolean) extends Action
case class UpdateDocumentRef(load: Boolean) extends Action
case class UpdateASTNodeRef(load: Boolean) extends Action
