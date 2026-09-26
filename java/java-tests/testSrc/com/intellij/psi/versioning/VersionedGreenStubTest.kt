// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.versioning

import com.intellij.extapi.psi.StubBasedPsiElementBase
import com.intellij.openapi.application.readAction
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.impl.source.PsiFileImpl
import com.intellij.psi.util.PsiVersioningService
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.editorFixture
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.psiFileFixture
import com.intellij.testFramework.junit5.fixture.sourceRootFixture
import com.intellij.util.concurrency.ThreadingAssertions
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.lang.ref.Reference

/**
 * Stubs are not parameterized by PSI versions, so a versioned snapshot must not hand out stub data
 * (see `docs/IntelliJ-Platform/4_man/Lang/Versioned-Syntax-Trees.md`, section "Stubs").
 *
 * Green stubs are reachable through several entry points, and all of them are covered here: the deprecated
 * [PsiFileImpl.getGreenStubTree] and [PsiFileImpl.getGreenStub], their non-deprecated replacements
 * [PsiFileImpl.withGreenStubOrAst] and [PsiFileImpl.withGreenStubTreeOrAst], and
 * [com.intellij.psi.impl.source.SpineRef] for a stubbed element. `SpineRef` is the only substrate that can serve a stub
 * while the AST is loaded -- every other substrate already returns `null` for a stubbed element whose file has an AST.
 */
@TestApplication
internal class VersionedGreenStubTest {

  private companion object {
    private val TEXT = """
      class Small {
        void existing() {
        }
      }
    """.trimIndent()

    private const val STUB_BRANCH = "stub"
    private const val AST_BRANCH = "ast"

    /** Reports which of the two processors [PsiFileImpl.withGreenStubOrAst] picked, without inspecting the payload. */
    private fun PsiFileImpl.branchTakenByWithGreenStubOrAst(): String =
      withGreenStubOrAst({ STUB_BRANCH }, { AST_BRANCH })

    private fun PsiFileImpl.branchTakenByWithGreenStubTreeOrAst(): String =
      withGreenStubTreeOrAst({ STUB_BRANCH }, { AST_BRANCH })
  }

  private val _project = projectFixture(openAfterCreation = true)
  private val _module = _project.moduleFixture("src")
  private val _sourceRoot = _module.sourceRootFixture()
  private val _psiFile = _sourceRoot.psiFileFixture("Small.java", TEXT)
  private val _editor = _psiFile.editorFixture()

  private val project by _project
  private val editor by _editor

  @BeforeEach
  fun awaitIndexing() {
    IndexingTestUtil.waitUntilIndexesAreReady(project)
  }

  @Test
  fun `frozen psi version does not observe green stubs`() = timeoutRunBlocking {
    withCoexistingStubsAndAst { file, spineElement ->
      PsiVersioningService.freezePsiVersion {
        // A frozen version degrades to a plain read action when versioning is switched off, and there the green stub
        // stays available -- the assertions below would then pass for the wrong reason, so fail loudly instead.
        ThreadingAssertions.assertNoReadAccess()
        assertGreenStubDisabled(file, spineElement)
      }
    }
  }

  /**
   * Brings the PSI of the tested file into the state where the stub tree and the AST coexist -- the only state in which
   * `FileTrees` installs [com.intellij.psi.impl.source.SpineRef] substrates and a green stub is observable at all --
   * and runs [action] on the file together with one of its spine-backed elements.
   */
  @Suppress("DEPRECATION")
  private suspend fun withCoexistingStubsAndAst(action: (PsiFileImpl, StubBasedPsiElementBase<*>) -> Unit) {
    val (file, spineElement, stubTree) = readAction {
      val javaFile = (PsiDocumentManager.getInstance(project).getPsiFile(editor.document) ?: error("PSI file should exist for ${editor.document}")) as PsiJavaFile
      val fileImpl = javaFile as PsiFileImpl
      // The IDE normally reaches this state by loading stubs from the index and then parsing; building the stubs on top
      // of the AST is the deterministic way to reach the same `FileTrees` state from a test.
      val stubTree = fileImpl.calcStubTree()
      val psiClass = javaFile.classes.single() as StubBasedPsiElementBase<*>
      Assertions.assertNull(psiClass.stub, "The AST is loaded, so the plain stub must be unavailable")
      Assertions.assertNotNull(psiClass.greenStub, "Stubs and the AST coexist, so the green stub must be available")
      Assertions.assertSame(stubTree, fileImpl.greenStubTree, "The freshly built stub tree must be the file's green stub tree")
      Assertions.assertEquals(STUB_BRANCH, fileImpl.branchTakenByWithGreenStubOrAst())
      Assertions.assertEquals(STUB_BRANCH, fileImpl.branchTakenByWithGreenStubTreeOrAst())
      Triple(fileImpl, psiClass, stubTree)
    }

    action(file, spineElement)

    readAction {
      Assertions.assertNotNull(spineElement.greenStub, "Leaving the versioned environment must bring the green stub back")
      Assertions.assertSame(stubTree, file.greenStubTree, "Leaving the versioned environment must bring the green stub tree back")
      Assertions.assertEquals(STUB_BRANCH, file.branchTakenByWithGreenStubOrAst())
      Assertions.assertEquals(STUB_BRANCH, file.branchTakenByWithGreenStubTreeOrAst())
    }
    // `FileTrees` holds the stub tree only softly, so keep it alive until the last assertion: a collection in between
    // would null out the green stub for a reason that has nothing to do with versioning.
    Reference.reachabilityFence(stubTree)
  }

  @Suppress("DEPRECATION")
  private fun assertGreenStubDisabled(file: PsiFileImpl, spineElement: StubBasedPsiElementBase<*>) {
    Assertions.assertNull(
      file.greenStubTree,
      "Stubs are not versioned, so a versioned snapshot must not observe the green stub tree of $file",
    )
    Assertions.assertNull(file.greenStub, "The green stub of $file is the root of its green stub tree, so it must be gone too")
    Assertions.assertNull(
      spineElement.greenStub,
      "Stubs are not versioned, so a versioned snapshot must not observe the green stub of $spineElement",
    )
    // The non-deprecated replacements for the two getters above must route to the AST for the same reason.
    Assertions.assertEquals(
      AST_BRANCH, file.branchTakenByWithGreenStubOrAst(),
      "withGreenStubOrAst must not hand a versioned snapshot the stub of $file",
    )
    Assertions.assertEquals(
      AST_BRANCH, file.branchTakenByWithGreenStubTreeOrAst(),
      "withGreenStubTreeOrAst must not hand a versioned snapshot the stub tree of $file",
    )
    // With no green stub to walk, stub-based navigation has to fall back to the versioned AST.
    Assertions.assertSame(file, spineElement.parent)
  }
}
