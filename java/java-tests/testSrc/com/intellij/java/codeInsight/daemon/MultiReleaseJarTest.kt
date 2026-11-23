// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.daemon

import com.intellij.codeInsight.completion.CompletionType
import com.intellij.java.testFramework.fixtures.lightJava9ModulesFixture
import com.intellij.java.testFramework.fixtures.lightJava9ModulesProjectFixture
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.project.DumbService.Companion.getInstance
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.pom.java.LanguageLevel
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiJavaReference
import com.intellij.psi.impl.search.JavaVersionBasedScope
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.ProjectScope
import com.intellij.psi.search.PsiShortNamesCache
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.stubs.StubUpdatingIndex
import com.intellij.testFramework.DumbModeTestUtils.runInDumbModeSynchronously
import com.intellij.testFramework.IdeaTestUtil
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.util.indexing.FileBasedIndex
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

private const val CLASS_NAME = "com.example.MultiReleaseClass"

@TestApplication
class MultiReleaseJarTest {
  companion object {
    private val projectFixture = lightJava9ModulesProjectFixture()
  }

  private val java9 by lightJava9ModulesFixture(projectFixture)
  private val myFixture get() = java9.fixture

  private lateinit var scope: GlobalSearchScope

  @BeforeEach
  fun setUp() {
    scope = ProjectScope.getLibrariesScope(myFixture.project)
  }

  @AfterEach
  fun tearDown() {
    scope = GlobalSearchScope.EMPTY_SCOPE
  }

  @Test
  fun testResolve() {
    IdeaTestUtil.withLevel(myFixture.module, LanguageLevel.JDK_1_8) {
      myFixture.configureByText("a.java", "import com.example.*;\nclass a { <caret>MultiReleaseClass f; }")
      runReadActionBlocking { assertUnversioned((myFixture.getReferenceAtCaretPosition() as PsiJavaReference).multiResolve(false).map { it.element }) }
    }
  }

  @Test
  fun testResolve9() {
    IdeaTestUtil.withLevel(myFixture.module, LanguageLevel.JDK_1_9) {
      myFixture.configureByText("a.java", "import com.example.*;\nclass a { <caret>MultiReleaseClass f; }")
      runReadActionBlocking { assertVersioned((myFixture.getReferenceAtCaretPosition() as PsiJavaReference).multiResolve(false).map { it.element }) }
    }
  }

  @Test
  fun testResolve11DumbMode() {
    IdeaTestUtil.withLevel(myFixture.module, LanguageLevel.JDK_11) {
      runInDumbModeSynchronously(myFixture.project) {
        getInstance(myFixture.project).withAlternativeResolveEnabled {
          myFixture.configureByText("a.java", """
            import com.example.MultiReleaseClass;
            class a {
              <caret>MultiReleaseClass f;
            }""".trimIndent())
          runReadActionBlocking { assertVersioned((myFixture.getReferenceAtCaretPosition() as PsiJavaReference).multiResolve(false).map { it.element }) }
        }
      }
    }
  }

  @Test
  fun testCompletion() {
    IdeaTestUtil.withLevel(myFixture.module, LanguageLevel.JDK_1_8) {
      myFixture.configureByText("a.java", "class a { MultiReleaseClass<caret> f; }")
      assertUnversioned(myFixture.complete(CompletionType.BASIC).map { it.psiElement })
    }
  }

  @Test
  fun testCompletion9() {
    IdeaTestUtil.withLevel(myFixture.module, LanguageLevel.JDK_1_9) {
      myFixture.configureByText("a.java", "class a { MultiReleaseClass<caret> f; }")
      assertVersioned(myFixture.complete(CompletionType.BASIC).map { it.psiElement })
    }
  }

  @Test
  fun testCompletionMethod() {
    IdeaTestUtil.withLevel(myFixture.module, LanguageLevel.JDK_1_8) {
      myFixture.configureByText("a.java", """
        import com.example.MultiReleaseClass;
        class a {
          Object test() {
            return MultiReleaseClass.class.getDeclaredMethod("jav<caret>");
          }
        }
        """.trimIndent())
      myFixture.complete(CompletionType.BASIC)
      myFixture.type('\n')
      myFixture.checkResult("""
        import com.example.MultiReleaseClass;
        class a {
          Object test() {
            return MultiReleaseClass.class.getDeclaredMethod("jav" +
                    "");
          }
        }
        """.trimIndent())
    }
  }

  @Test
  fun testCompletionMethod9() {
    IdeaTestUtil.withLevel(myFixture.module, LanguageLevel.JDK_1_9) {
      myFixture.configureByText("a.java", """
        import com.example.MultiReleaseClass;
        class a {
          Object test() {
            return MultiReleaseClass.class.getDeclaredMethod("jav<caret>");
          }
        }
        """.trimIndent())
      myFixture.complete(CompletionType.BASIC)
      myFixture.type('\n')
      myFixture.checkResult("""
        import com.example.MultiReleaseClass;
        class a {
          Object test() {
            return MultiReleaseClass.class.getDeclaredMethod("multiReleaseJava9Impl");
          }
        }
        """.trimIndent())
    }
  }

  @Test
  fun testCompletionMethod11() {
    IdeaTestUtil.withLevel(myFixture.module, LanguageLevel.JDK_11) {
      myFixture.configureByText("a.java", """
        import com.example.MultiReleaseClass;
        class a {
          Object test() {
            return MultiReleaseClass.class.getDeclaredMethod("jav<caret>");
          }
        }
        """.trimIndent())
      myFixture.complete(CompletionType.BASIC)
      myFixture.type('\n')
      myFixture.checkResult("""
        import com.example.MultiReleaseClass;
        class a {
          Object test() {
            return MultiReleaseClass.class.getDeclaredMethod("multiReleaseJava10Impl");
          }
        }
        """.trimIndent())
    }
  }

  @Test
  fun testFindClassByFullName(): Unit = runReadActionBlocking {
    assertUnversioned(myFixture.javaFacade.findClass(CLASS_NAME, scope8()))
    assertVersioned(myFixture.javaFacade.findClass(CLASS_NAME, scope9()))
  }

  @Test
  fun testResolveFromMultiRelease(): Unit = runReadActionBlocking {
    val facade = myFixture.javaFacade
    val class8 = facade.findClass(CLASS_NAME, scope8())!!
    val class9 = facade.findClass(CLASS_NAME, scope9())!!
    assertNotEquals(class8, class9)
    val another8 = facade.findClass("com.example.Another", scope8())!!
    val another9 = facade.findClass("com.example.Another", scope9())!!
    assertEquals(8, another8.fields[0].computeConstantValue())
    assertEquals(9, another9.fields[0].computeConstantValue())
    val method8 = class8.findMethodsByName("ver8", false)[0]!!
    val method9 = class9.findMethodsByName("ver9", false)[0]!!
    assertEquals(another8, (method8.returnType as PsiClassType).resolve())
    assertEquals(another9, (method9.returnType as PsiClassType).resolve())
  }
  
  @Test
  fun testReferenceSearch() {
    val facade = myFixture.javaFacade
    val (class8, class9) = runReadActionBlocking {
      val class8 = facade.findClass(CLASS_NAME, scope8())!!
      assertUnversioned(class8)
      val class9 = facade.findClass(CLASS_NAME, scope9())!!
      assertVersioned(class9)
      class8 to class9
    }
    myFixture.configureByText("Test.java", "import com.example.*; class Test extends MultiReleaseClass {}")
    for(level in listOf(LanguageLevel.JDK_1_8, LanguageLevel.JDK_1_9)) {
      IdeaTestUtil.withLevel(myFixture.module, level) {
        var refs = runReadActionBlocking { ReferencesSearch.search(class8).findAll() }
        assertEquals(1, refs.size)
        assertEquals(myFixture.file, runReadActionBlocking { refs.first().element.containingFile })
        refs = runReadActionBlocking { ReferencesSearch.search(class9).findAll() }
        assertEquals(1, refs.size)
        assertEquals(myFixture.file, runReadActionBlocking { refs.first().element.containingFile })
      }
    }
  }

  @Test
  fun testClassPresentation(): Unit = runReadActionBlocking {
    assertEquals("(com.example)", myFixture.javaFacade.findClass(CLASS_NAME, scope8())!!.presentation!!.locationString)
    assertEquals("(com.example/ver. 9)", myFixture.javaFacade.findClass(CLASS_NAME, scope9())!!.presentation!!.locationString)
    assertEquals("(com.example/ver. 10)", myFixture.javaFacade.findClass(CLASS_NAME, scope10())!!.presentation!!.locationString)
  }

  @Test
  fun testFindClassByShortName(): Unit = runReadActionBlocking {
    assertUnversioned(PsiShortNamesCache.getInstance(myFixture.project).getClassesByName("MultiReleaseClass", scope8()).toList())
    assertVersioned(PsiShortNamesCache.getInstance(myFixture.project).getClassesByName("MultiReleaseClass", scope9()).toList())
    assertVersioned(PsiShortNamesCache.getInstance(myFixture.project).getClassesByName("MultiReleaseClass", scope10()).toList())
  }

  @Test
  fun testFindMethod(): Unit = runReadActionBlocking {
    assertUnversioned(PsiShortNamesCache.getInstance(myFixture.project).getMethodsByName("multiReleaseDefaultImpl", scope8()).toList())
    assertThat(PsiShortNamesCache.getInstance(myFixture.project).getMethodsByName("multiReleaseJava9Impl", scope8())).isEmpty()
    assertVersioned(PsiShortNamesCache.getInstance(myFixture.project).getMethodsByName("multiReleaseJava9Impl", scope9()).toList())
  }

  private fun scope8() = JavaVersionBasedScope(myFixture.project, scope, LanguageLevel.JDK_1_8)

  private fun scope9() = JavaVersionBasedScope(myFixture.project, scope, LanguageLevel.JDK_1_9)

  private fun scope10() = JavaVersionBasedScope(myFixture.project, scope, LanguageLevel.JDK_10)

  @Test
  fun testFindFile() {
    assertThat(getAllAvailableVersionClasses()).hasSize(3)
  }

  @Test
  fun testStubIndexInternals(): Unit = runReadActionBlocking {
    for (file in getAllAvailableVersionClasses()) {
      val data = FileBasedIndex.getInstance().getFileData(StubUpdatingIndex.INDEX_ID, file, myFixture.project)
      val stub = assertThat(data.values).singleElement().actual()
      assertNotNull(stub)
    }
  }

  private fun getAllAvailableVersionClasses(): Collection<VirtualFile> =
    runReadActionBlocking { FilenameIndex.getVirtualFilesByName("MultiReleaseClass.class", scope) }

  private fun assertUnversioned(elements: List<PsiElement?>) {
    assertThat(elements).hasSize(1)
    assertUnversioned(elements[0])
  }

  private fun assertUnversioned(element: PsiElement?) {
    assertThat(element).isNotNull
    assertThat(element!!.containingFile.virtualFile.path).doesNotContain("/META-INF/versions/")
  }

  private fun assertVersioned(elements: List<PsiElement?>) {
    assertThat(elements).hasSize(1)
    assertVersioned(elements[0])
  }

  private fun assertVersioned(element: PsiElement?) {
    assertThat(element).isNotNull
    assertThat(element!!.containingFile.virtualFile.path).contains("/META-INF/versions/")
  }
}