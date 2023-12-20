// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.daemon

import com.intellij.codeInsight.completion.CompletionType
import com.intellij.java.testFramework.fixtures.LightJava9ModulesCodeInsightFixtureTestCase
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
import com.intellij.testFramework.IdeaTestUtil
import com.intellij.util.indexing.FileBasedIndex
import org.assertj.core.api.Assertions.assertThat
import kotlin.test.assertNotEquals

private const val CLASS_NAME = "com.example.MultiReleaseClass"

class MultiReleaseJarTest : LightJava9ModulesCodeInsightFixtureTestCase() {
  private lateinit var scope: GlobalSearchScope

  override fun setUp() {
    super.setUp()
    scope = ProjectScope.getLibrariesScope(project)
  }

  override fun tearDown() {
    scope = GlobalSearchScope.EMPTY_SCOPE
    super.tearDown()
  }

  fun testResolve() {
    IdeaTestUtil.withLevel(module, LanguageLevel.JDK_1_8) {
      myFixture.configureByText("a.java", "import com.example.*;\nclass a { <caret>MultiReleaseClass f; }")
      assertUnversioned((myFixture.getReferenceAtCaretPosition() as PsiJavaReference).multiResolve(false).map { it.element })
    }
  }

  fun testResolve9() {
    IdeaTestUtil.withLevel(module, LanguageLevel.JDK_1_9) {
      myFixture.configureByText("a.java", "import com.example.*;\nclass a { <caret>MultiReleaseClass f; }")
      assertVersioned((myFixture.getReferenceAtCaretPosition() as PsiJavaReference).multiResolve(false).map { it.element })
    }
  }

  fun testCompletion() {
    IdeaTestUtil.withLevel(module, LanguageLevel.JDK_1_8) {
      myFixture.configureByText("a.java", "class a { MultiReleaseClass<caret> f; }")
      assertUnversioned(myFixture.complete(CompletionType.BASIC).map { it.psiElement })
    }
  }

  fun testCompletion9() {
    IdeaTestUtil.withLevel(module, LanguageLevel.JDK_1_9) {
      myFixture.configureByText("a.java", "class a { MultiReleaseClass<caret> f; }")
      assertVersioned(myFixture.complete(CompletionType.BASIC).map { it.psiElement })
    }
  }

  fun testCompletionMethod() {
    IdeaTestUtil.withLevel(module, LanguageLevel.JDK_1_8) {
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

  fun testCompletionMethod9() {
    IdeaTestUtil.withLevel(module, LanguageLevel.JDK_1_9) {
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

  fun testCompletionMethod11() {
    IdeaTestUtil.withLevel(module, LanguageLevel.JDK_11) {
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

  fun testFindClassByFullName() {
    assertUnversioned(myFixture.javaFacade.findClass(CLASS_NAME, scope8()))
    assertVersioned(myFixture.javaFacade.findClass(CLASS_NAME, scope9()))
  }

  fun testResolveFromMultiRelease() {
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
  
  fun testReferenceSearch() {
    val facade = myFixture.javaFacade
    val class8 = facade.findClass(CLASS_NAME, scope8())!!
    assertUnversioned(class8)
    val class9 = facade.findClass(CLASS_NAME, scope9())!!
    assertVersioned(class9)
    myFixture.configureByText("Test.java", "import com.example.*; class Test extends MultiReleaseClass {}")
    IdeaTestUtil.withLevel(module, LanguageLevel.JDK_1_8) {
      val refs = ReferencesSearch.search(class8).findAll()
      assertEquals(1, refs.size)
      assertEquals(myFixture.file, refs.first().element.containingFile)
      assertEmpty(ReferencesSearch.search(class9).findAll())
    }
    IdeaTestUtil.withLevel(module, LanguageLevel.JDK_1_9) {
      val refs = ReferencesSearch.search(class9).findAll()
      assertEquals(1, refs.size)
      assertEquals(myFixture.file, refs.first().element.containingFile)
      assertEmpty(ReferencesSearch.search(class8).findAll())
    }
  }

  fun testClassPresentation() {
    assertEquals("(com.example)", myFixture.javaFacade.findClass(CLASS_NAME, scope8())!!.presentation!!.locationString)
    assertEquals("(com.example/ver. 9)", myFixture.javaFacade.findClass(CLASS_NAME, scope9())!!.presentation!!.locationString)
    assertEquals("(com.example/ver. 10)", myFixture.javaFacade.findClass(CLASS_NAME, scope10())!!.presentation!!.locationString)
  }

  fun testFindClassByShortName() {
    assertUnversioned(PsiShortNamesCache.getInstance(project).getClassesByName("MultiReleaseClass", scope8()).toList())
    assertVersioned(PsiShortNamesCache.getInstance(project).getClassesByName("MultiReleaseClass", scope9()).toList())
    assertVersioned(PsiShortNamesCache.getInstance(project).getClassesByName("MultiReleaseClass", scope10()).toList())
  }

  fun testFindMethod() {
    assertUnversioned(PsiShortNamesCache.getInstance(project).getMethodsByName("multiReleaseDefaultImpl", scope8()).toList())
    assertThat(PsiShortNamesCache.getInstance(project).getMethodsByName("multiReleaseJava9Impl", scope8())).isEmpty()
    assertVersioned(PsiShortNamesCache.getInstance(project).getMethodsByName("multiReleaseJava9Impl", scope9()).toList())
  }

  private fun scope8() = JavaVersionBasedScope(project, scope, LanguageLevel.JDK_1_8)

  private fun scope9() = JavaVersionBasedScope(project, scope, LanguageLevel.JDK_1_9)

  private fun scope10() = JavaVersionBasedScope(project, scope, LanguageLevel.JDK_10)

  fun testFindFile() {
    assertThat(getAllAvailableVersionClasses()).hasSize(3)
  }

  fun testStubIndexInternals() {
    for (file in getAllAvailableVersionClasses()) {
      val data = FileBasedIndex.getInstance().getFileData(StubUpdatingIndex.INDEX_ID, file, project)
      val stub = assertOneElement(data.values)
      assertNotNull(stub)
    }
  }

  private fun getAllAvailableVersionClasses(): Collection<VirtualFile> =
    FilenameIndex.getVirtualFilesByName("MultiReleaseClass.class", scope)

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