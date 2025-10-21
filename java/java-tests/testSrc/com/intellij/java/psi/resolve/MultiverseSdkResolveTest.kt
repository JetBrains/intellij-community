// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.psi.resolve

import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.writeAction
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.platform.testFramework.junit5.projectStructure.fixture.multiverseProjectFixture
import com.intellij.psi.PsiManager
import com.intellij.testFramework.IdeaTestUtil
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.testFixture
import org.junit.jupiter.api.Test
import kotlin.io.path.Path
import kotlin.test.assertNotNull

@TestApplication
internal class MultiverseSdkResolveTest {
  companion object {

    val projectFixture = multiverseProjectFixture(openAfterCreation = true) {
      module("foo") {
        //useSdk("jdk")
        contentRoot("root") {
          sourceRoot("root") {
            file("A.java", """
              class A {
                void foo(String s){} 
              }
            """.trimIndent())
          }
        }
      }
    }

    @Suppress("unused")
    val setupSdk = testFixture {
      val project = projectFixture.init()
      val sdk = IdeaTestUtil.getMockJdk21()
      writeAction {
        ProjectJdkTable.getInstance().addJdk(sdk, project)
        val module = ModuleManager.getInstance(project).modules.find { it.name == "foo" }!!
        ModuleRootManager.getInstance(module).modifiableModel.apply {
          this.sdk = sdk
        }.commit()
      }
      initialized(Unit) {}
    }
  }

  @Test
  fun `test resolve`(): Unit = timeoutRunBlocking {
    val project = projectFixture.get()
    IndexingTestUtil.waitUntilIndexesAreReady(project)
    val file = requireNotNull(VfsUtil.findFile(Path(project.basePath!!, "foo/root/root/A.java"), true))
    readAction {
      val psiFile = requireNotNull(PsiManager.getInstance(project).findFile(file))
      val offset = psiFile.text.indexOf("String")
      val ref = requireNotNull(psiFile.findReferenceAt(offset))
      val resolved = ref.resolve()
      assertNotNull(resolved)
    }
  }
}