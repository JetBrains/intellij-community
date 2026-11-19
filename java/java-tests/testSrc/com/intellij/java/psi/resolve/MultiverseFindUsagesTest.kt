// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.psi.resolve

import com.intellij.codeInsight.multiverse.CodeInsightContextManager
import com.intellij.codeInsight.multiverse.codeInsightContext
import com.intellij.find.FindManager
import com.intellij.find.findUsages.FindUsagesManager
import com.intellij.find.impl.FindManagerImpl
import com.intellij.openapi.application.readAction
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.util.ProgressIndicatorBase
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.platform.testFramework.junit5.projectStructure.fixture.multiverseProjectFixture
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.usages.Usage
import com.intellij.usages.UsageInfo2UsageAdapter
import com.intellij.util.CommonProcessors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.Collections
import kotlin.io.path.Path

@TestApplication
internal class MultiverseFindUsagesTest {
  companion object {
    private const val DECLARATION = """
      public class A {
        public void foo() {}
      }
    """

    private const val USAGE = """
      public class B {
        void bar() {
          new A().foo();
        }
      }
    """

    private val projectFixture = multiverseProjectFixture(openAfterCreation = true) {
      module("lib") {
        contentRoot("root") {
          sourceRoot("src") {
            file("A.java", DECLARATION)
          }
        }
      }
      module("a") {
        dependencies {
          module("lib")
        }
        contentRoot("root") {
          sourceRoot("src", "shared") {
            file("B.java", USAGE)
          }
        }
      }
      module("b") {
        dependencies {
          module("lib")
        }
        sharedSourceRoot("shared")
      }
    }
  }

  @Test
  fun `usage of an instance method in a shared file is found once`(): Unit = timeoutRunBlocking {
    val project = projectFixture.get()
    IndexingTestUtil.waitUntilIndexesAreReady(project)
    val usageFile = VfsUtil.findFile(Path(project.basePath!!, "a/root/src/B.java"), true)!!
    val contexts = readAction { CodeInsightContextManager.getInstance(project).getCodeInsightContexts(usageFile) }
    assertEquals(2, contexts.size, "B.java must have a context for each module: $contexts")

    val locations = findUsagesOfFoo(project, "lib/root/src/A.java")
    assertEquals(listOf("B.java"), locations.map { it.substringBefore(':') }, "each usage must be found once: $locations")
  }

  /**
   * @return the usages of `A.foo`, as `file:offset:context`
   */
  private suspend fun findUsagesOfFoo(project: Project, declarationPath: String): List<String> {
    val file = VfsUtil.findFile(Path(project.basePath!!, declarationPath), true)!!
    val target = readAction {
      val psiFile = PsiManager.getInstance(project).findFile(file)!!
      PsiTreeUtil.getParentOfType(psiFile.findElementAt(psiFile.text.indexOf("foo")), PsiNamedElement::class.java)!!
    }
    val usages = findUsages(project, target)
    return readAction {
      usages.map { (it as UsageInfo2UsageAdapter).usageInfo }.map {
        "${it.virtualFile?.name}:${it.navigationRange.startOffset}:${it.file?.codeInsightContext}"
      }
    }
  }

  private suspend fun findUsages(project: Project, target: PsiNamedElement): List<Usage> {
    val searcher = readAction {
      val handler = (FindManager.getInstance(project) as FindManagerImpl).findUsagesManager.getFindUsagesHandler(target, false)!!
      val options = handler.getFindUsagesOptions(null)
      options.searchScope = GlobalSearchScope.allScope(project)
      FindUsagesManager.createUsageSearcher(handler, handler.primaryElements, handler.secondaryElements, options)
    }
    return withContext(Dispatchers.Default) {
      val processor = CommonProcessors.CollectProcessor(Collections.synchronizedList(ArrayList<Usage>()))
      ProgressManager.getInstance().runProcess({ searcher.generate(processor) }, ProgressIndicatorBase())
      processor.results.toList()
    }
  }
}
