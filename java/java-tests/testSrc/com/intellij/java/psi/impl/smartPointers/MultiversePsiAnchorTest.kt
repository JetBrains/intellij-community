package com.intellij.java.psi.impl.smartPointers

import com.intellij.codeInsight.multiverse.ProjectModelContextBridge
import com.intellij.openapi.application.readAction
import com.intellij.openapi.module.Module
import com.intellij.platform.testFramework.junit5.projectStructure.fixture.withSharedSourceEnabled
import com.intellij.psi.PsiAnchor
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiManager
import com.intellij.psi.impl.file.impl.sharedSourceRootFixture
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.psiFileFixture
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

@TestApplication
internal class MultiversePsiAnchorTest {
  private val sharedFileFixture = sharedSourceRootFixture.psiFileFixture("A.java", "// comment\nclass A {}")
  private val project get() = projectFixture.get()

  @Test
  fun `anchors in different contexts are not equal`() = timeoutRunBlocking {
    val virtualFile = sharedFileFixture.get().virtualFile
    IndexingTestUtil.suspendUntilIndexesAreReady(project)
    readAction {
      val (fileA, fileB) = listOf(moduleAFixture.get(), moduleBFixture.get()).map {
        val context = ProjectModelContextBridge.getInstance(project).getContext(it)!!
        PsiManager.getInstance(project).findFile(virtualFile, context)!!
      }

      val anchorsA = createAnchorOfEachKind(fileA)
      val anchorsB = createAnchorOfEachKind(fileB)
      assertEquals(listOf("PsiFileReference", "TreeRangeReference", "StubIndexReference"), anchorsA.map { it.javaClass.simpleName },
                   "the test must cover each kind of anchor")
      val anchorsAgainA = createAnchorOfEachKind(fileA)
      assertEquals(anchorsA, anchorsAgainA, "anchors of the same element must be equal")
      assertEquals(anchorsA.map { it.hashCode() }, anchorsAgainA.map { it.hashCode() }, "equal anchors must have equal hash codes")
      for ((anchorA, anchorB) in anchorsA.zip(anchorsB)) {
        assertNotEquals(anchorA, anchorB, "anchors in different contexts must not be equal")
        assertEquals(fileA, anchorA.file, "an anchor must restore the file of its context")
        assertEquals(fileB, anchorB.file, "an anchor must restore the file of its context")
      }
    }
  }

  private fun createAnchorOfEachKind(file: PsiFile): List<PsiAnchor> = listOf(
    PsiAnchor.create(file),
    PsiAnchor.create(PsiTreeUtil.findChildOfType(file, PsiComment::class.java)!!),
    PsiAnchor.create((file as PsiJavaFile).classes.single()),
  )

  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true).withSharedSourceEnabled()
    private val moduleAFixture: TestFixture<Module> = projectFixture.moduleFixture("moduleA")
    private val moduleBFixture: TestFixture<Module> = projectFixture.moduleFixture("moduleB")
    private val sharedSourceRootFixture = sharedSourceRootFixture(moduleAFixture, moduleBFixture)
  }
}
