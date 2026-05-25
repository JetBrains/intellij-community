// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.file.impl

import com.intellij.codeInsight.multiverse.CodeInsightContextManagerImpl
import com.intellij.codeInsight.multiverse.ModuleContext
import com.intellij.codeInsight.multiverse.codeInsightContext
import com.intellij.codeInsight.multiverse.defaultContext
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.application.readAction
import com.intellij.openapi.module.ModuleType
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.platform.testFramework.junit5.projectStructure.fixture.multiverseProjectFixture
import com.intellij.psi.PsiManager
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.EnableTracingFor
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.utils.vfs.createFile
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.RepeatedTest
import kotlin.io.path.Path

@EnableTracingFor(
  categories = ["#com.intellij.psi.impl.file.impl.MultiverseFileViewProviderCache"],
  categoryClasses = [CodeInsightContextManagerImpl::class]
)
@TestApplication
internal class FileInvalidationTest {
  private val projectFixture = multiverseProjectFixture(withSharedSourceEnabled = true) {}

  private val project get() = projectFixture.get()

  /**
   * Verifies that a PSI file's [codeInsightContext] is correctly invalidated and re-resolved
   * when the project's module structure changes:
   *
   * 1. With no modules, the file has [defaultContext].
   * 2. After adding `module1` (content root = project root), the context becomes `ModuleContext("module1")`.
   * 3. After adding `module2` (same root), the context stays `module1` (first module wins).
   * 4. After removing `module1`'s roots, the context switches to `module2`.
   *
   * Repeated 1000 times to catch race conditions in invalidation.
   */
  @RepeatedTest(value = 100)
  fun `test default context invalidates`() = timeoutRunBlocking {
    // Step 1: no modules
    val root = readAction { VfsUtil.findFile(Path(project.basePath!!), false)!! }
    val virtualFile = edtWriteAction { root.createFile("foo.txt") }

    val psiFileDefaultContext = readAction { PsiManager.getInstance(project).findFile(virtualFile)!! }

    val initialContext = readAction { psiFileDefaultContext.codeInsightContext }
    Assertions.assertEquals(
      defaultContext(), initialContext,
      "Step 1 (no modules added yet): file should have defaultContext, but was $initialContext (${initialContext::class.java.name})",
    )

    // Step 2: adding module1
    val module1 = PsiTestUtil.addModule(project, ModuleType.EMPTY, "module1", root)

    val psiFileModuleContext = readAction { PsiManager.getInstance(project).findFile(virtualFile)!! }

    val moduleContext = readAction { psiFileModuleContext.codeInsightContext }
    Assertions.assertInstanceOf(
      ModuleContext::class.java, moduleContext,
      "Step 2 (module1 added): expected ModuleContext, but was ${moduleContext::class.java.name} ($moduleContext)",
    )
    val moduleName1 = (moduleContext as ModuleContext).getModule()!!.name
    Assertions.assertEquals(
      "module1", moduleName1,
      "Step 2 (module1 added): module name should be 'module1', but ModuleContext resolved to '$moduleName1'",
    )

    // Step 3: adding module2 on same root
    PsiTestUtil.addModule(project, ModuleType.EMPTY, "module2", root)

    val psiFileModuleContext2 = readAction { PsiManager.getInstance(project).findFile(virtualFile)!! }
    val moduleContext2 = readAction { psiFileModuleContext2.codeInsightContext }
    Assertions.assertInstanceOf(
      ModuleContext::class.java, moduleContext2,
      "Step 3 (module2 added on same root): expected ModuleContext, but was ${moduleContext2::class.java.name} ($moduleContext2)",
    )
    val moduleName2 = (moduleContext2 as ModuleContext).getModule()!!.name
    Assertions.assertEquals(
      "module1", moduleName2,
      "Step 3 (module2 added on same root): first module should still win, expected 'module1' but got '$moduleName2'",
    )
    psiFileModuleContext.hashCode() // keep a hard reference to the file with module1 context, so that it doesn't get GCed and make sure it keeps being preferred

    // Step 4: removing module1 roots
    PsiTestUtil.removeAllRoots(module1, null)

    val psiFileModuleContext3 = readAction { PsiManager.getInstance(project).findFile(virtualFile)!! }
    val moduleContext3 = readAction { psiFileModuleContext3.codeInsightContext }
    Assertions.assertInstanceOf(
      ModuleContext::class.java, moduleContext3,
      "Step 4 (module1 roots removed): expected ModuleContext, but was ${moduleContext3::class.java.name} ($moduleContext3)",
    )
    val moduleName3 = (moduleContext3 as ModuleContext).getModule()!!.name
    Assertions.assertEquals(
      "module2", moduleName3,
      "Step 4 (module1 roots removed): context should switch to 'module2', but got '$moduleName3'",
    )
  }
}