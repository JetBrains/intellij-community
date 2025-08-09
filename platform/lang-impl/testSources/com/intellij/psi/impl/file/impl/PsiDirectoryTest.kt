// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.file.impl

import com.intellij.openapi.application.readAction
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.module.impl.scopes.ModulesScope
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.platform.testFramework.junit5.projectStructure.fixture.multiverseProjectFixture
import com.intellij.psi.PsiManager
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Test
import kotlin.io.path.Path

@TestApplication
internal class PsiDirectoryTest {
  companion object {
    private val projectFixture = multiverseProjectFixture {
      module("foo") {
        contentRoot("root") {
          sourceRoot("src", SHARED_ID) {
            file("A.java", "")
          }
        }
      }
      module("bar") {
        sharedSourceRoot(SHARED_ID)
      }
    }
    private const val SHARED_ID = "shared"
  }

  @Test
  fun `PsiDirectory#getFiles should return all PsiFiles matching scope`() = timeoutRunBlocking {
    readAction {
      val project = projectFixture.get()
      val dirPath = Path(project.basePath!!, "foo/root/src")
      val vDir = VfsUtil.findFile(dirPath, false) ?: error("Can't find virtual directory $dirPath")
      val modules = ModuleManager.Companion.getInstance(project).modules
      assert(modules.size == 2) { modules.contentToString() }

      val modulesScope = ModulesScope(modules.toSet(), project)
      val psiDir = PsiManager.getInstance(project).findDirectory(vDir) ?: error("Can't find PsiDirectory for $vDir")

      // we have `psiDir` that matches `modulesScope` 2 times (once for the module `foo` and once for module `bar`)
      // expected result: psiDir.getFiles(modulesScope) returns two PsiFiles corresponding to A.java, one PsiFile in the context of `foo`, and another one in the scope of `bar`

      val files = psiDir.getFiles(modulesScope)
      assert(files.size == 2) { files.contentToString() }
      assert(files[0].name == "A.java")
      assert(files[1].name == "A.java")

      assert(files[0] != files[1])
    }
  }
}