// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.impl

import com.intellij.ide.highlighter.JavaFileType
import com.intellij.java.impl.template.JavaTemplateCodeInsightSupport
import com.intellij.java.impl.template.JavaTemplateFormattingSupport
import com.intellij.java.impl.template.JavaTemplateImportSupport
import com.intellij.java.impl.template.JavaTemplatePresentationSupport
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.smartReadAction
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.PsiImportList
import com.intellij.psi.PsiImportStatementBase
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.TokenType
import com.intellij.psi.codeStyle.JavaCodeStyleManager
import com.intellij.psi.impl.search.JavaIndexPatternBuilder
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.projectFixture
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

@TestApplication
@Timeout(30)
internal class JavaTemplateSupportTest {
  companion object {
    private val project = projectFixture(openAfterCreation = true)
  }

  private fun maskTemplateSupport(disposable: Disposable,
                                  codeInsight: List<JavaTemplateCodeInsightSupport> = emptyList(),
                                  imports: List<JavaTemplateImportSupport> = emptyList()) {
    ExtensionTestUtil.maskExtensions(JavaTemplateCodeInsightSupport.EP_NAME, codeInsight, disposable)
    ExtensionTestUtil.maskExtensions(JavaTemplateFormattingSupport.EP_NAME, emptyList(), disposable)
    ExtensionTestUtil.maskExtensions(JavaTemplateImportSupport.EP_NAME, imports, disposable)
    ExtensionTestUtil.maskExtensions(JavaTemplatePresentationSupport.EP_NAME, emptyList(), disposable)
  }

  @Test
  fun javaWorksWithoutTemplateExtensions(@TestDisposable disposable: Disposable): Unit = timeoutRunBlocking {
    maskTemplateSupport(disposable)
    smartReadAction(project.get()) {
      val file = javaFile()
      assertTrue(JavaTemplateImportSupport.getContexts(file).isEmpty())
      assertTrue(JavaTemplateImportSupport.isImportOptimizerAllowed(file))
      assertTrue(JavaTemplateImportSupport.isOnTheFlyOptimizationAllowed(file))
      assertEquals("List<String>", JavaTemplateCodeInsightSupport.escapeText(file, "List<String>"))
      assertSame(file, JavaTemplateCodeInsightSupport.getReferenceResolutionFile(file))
      assertTrue(JavaTemplatePresentationSupport.isJavaSourceAllowed(file))
      assertTrue(JavaTemplateFormattingSupport.isWhitespace(TokenType.WHITE_SPACE))
      assertNotNull(JavaIndexPatternBuilder().getIndexingLexer(file))
      assertNotNull(JavaIndexPatternBuilder().getCommentTokenSet(file))
      assertEquals(1, JavaCodeStyleManager.getInstance(file.project).findRedundantImports(file)!!.size)
    }
  }

  @Test
  fun preservesAnEmptyImportOverride(@TestDisposable disposable: Disposable): Unit = timeoutRunBlocking {
    val support = object : JavaTemplateImportSupport {
      override fun findRedundantImports(file: PsiJavaFile, imports: Array<PsiImportStatementBase>,
                                       uniqueImports: Set<PsiImportStatementBase>): Collection<PsiImportStatementBase> = emptyList()
    }
    maskTemplateSupport(disposable, imports = listOf(support))
    readAction {
      val file = javaFile()
      assertTrue(JavaCodeStyleManager.getInstance(file.project).findRedundantImports(file)!!.isEmpty())
    }
  }

  @Test
  fun appliesAllTransformationsAndVetoes(@TestDisposable disposable: Disposable): Unit = timeoutRunBlocking {
    val first = object : JavaTemplateCodeInsightSupport {
      override fun escapeInsertionText(file: PsiFile, text: String): String = text.replace("<", "&lt;")
    }
    val second = object : JavaTemplateCodeInsightSupport {
      override fun escapeInsertionText(file: PsiFile, text: String): String = text.replace(">", "&gt;")
      override fun allowsIndexingLexer(file: PsiFile): Boolean = false
    }
    maskTemplateSupport(disposable, codeInsight = listOf(first, second))
    readAction {
      val file = javaFile()
      assertEquals("List&lt;String&gt;", JavaTemplateCodeInsightSupport.escapeText(file, "List<String>"))
      assertFalse(JavaTemplateCodeInsightSupport.isIndexingLexerAllowed(file))
    }
  }

  @Test
  fun stopsAfterAnImportReplacement(@TestDisposable disposable: Disposable): Unit = timeoutRunBlocking {
    val calls = ArrayList<Int>()
    val extensions = (1..3).map { number ->
      object : JavaTemplateImportSupport {
        override fun replaceImportList(oldList: PsiImportList, newList: PsiImportList): Boolean {
          calls.add(number)
          return number == 2
        }
      }
    }
    maskTemplateSupport(disposable, imports = extensions)
    readAction {
      val list = javaFile().importList!!
      assertTrue(JavaTemplateImportSupport.replaceImports(list, list))
      assertEquals(listOf(1, 2), calls)
    }
  }

  @Test
  fun loadsHostClassesWithoutJspApis() {
    val names = setOf(
      "com.intellij.psi.impl.source.codeStyle.BraceEnforcer",
      "com.intellij.psi.impl.source.codeStyle.ImportHelper",
      "com.intellij.psi.impl.source.codeStyle.JavaCodeStyleManagerImpl",
      "com.intellij.psi.impl.source.codeStyle.JavaReferenceAdjuster",
      "com.intellij.psi.impl.source.tree.JavaTreeCopyHandler",
      "com.intellij.lang.java.JavaImportOptimizer",
      "com.intellij.codeInsight.completion.ModifierChooser",
      "com.intellij.codeInsight.completion.JavaCompletionUtil",
    )
    val loader = object : ClassLoader(javaClass.classLoader) {
      override fun loadClass(name: String, resolve: Boolean): Class<*> {
        if (name.startsWith("com.intellij.jsp.") || name.startsWith("com.intellij.psi.jsp.") ||
            name.startsWith("com.intellij.psi.impl.source.jsp.") || name == "com.intellij.psi.JspPsiUtil") {
          throw ClassNotFoundException(name)
        }
        if (name !in names) return super.loadClass(name, resolve)
        synchronized(getClassLoadingLock(name)) {
          val type = findLoadedClass(name) ?: run {
            val path = name.replace('.', '/') + ".class"
            val bytes = requireNotNull(parent.getResourceAsStream(path)).use { it.readBytes() }
            defineClass(name, bytes, 0, bytes.size)
          }
          if (resolve) resolveClass(type)
          return type
        }
      }
    }
    for (name in names) {
      val type = Class.forName(name, false, loader)
      assertSame(loader, type.classLoader)
      assertNotNull(type.declaredConstructors)
      assertNotNull(type.declaredMethods)
    }
  }

  private fun javaFile(): PsiJavaFile =
    PsiFileFactory.getInstance(project.get()).createFileFromText(
      "Example.java", JavaFileType.INSTANCE, "import java.util.List; class Example {}"
    ) as PsiJavaFile
}
