// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing

import com.intellij.lang.java.JavaParserDefinition
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase

class JavaPerFileElementTypeModificationTrackerTest : JavaCodeInsightFixtureTestCase() {
  companion object {
    val JAVA = JavaParserDefinition.JAVA_FILE!!
  }

  private val helper = StubIndexPerFileElementTypeModificationTrackerTestHelper()

  override fun setUp() {
    super.setUp()
    helper.setUp()
  }

  fun `test mod count increments on java file creation and change`() {
    helper.initModCounts(JAVA)
    val psi = myFixture.addFileToProject("Foo.java", "class Foo { String bar; }")
    helper.checkModCountHasChanged(JAVA)
    helper.ensureStubIndexUpToDate(project)
    runWriteAction { VfsUtil.saveText(psi.containingFile.virtualFile, "class Foo { int val; }"); }
    helper.checkModCountHasChanged(JAVA)
  }

  fun `test mod count doesnt change on non-stub changes`() {
    helper.initModCounts(JAVA)
    val psi = myFixture.addFileToProject("Foo.java", """
      class Predicate {
        boolean test(int x) {
          return true;
        }
      }
    """.trimIndent())
    helper.checkModCountHasChanged(JAVA)
    helper.ensureStubIndexUpToDate(project)
    runWriteAction { VfsUtil.saveText(psi.containingFile.virtualFile, """
      class Predicate {
        boolean test(int x) {
          return x >= 0;
        }
      }
    """.trimIndent()); }
    helper.checkModCountIsSame(JAVA)
  }

  fun `test mod count changes twice on same content if stub index is not updated`() {
    helper.initModCounts(JAVA)
    val src = """
      class Predicate {
        boolean test(int x) {
          return true;
        }
      }
    """.trimIndent()
    val psi = myFixture.addFileToProject("Foo.java", src)
    helper.checkModCountHasChanged(JAVA)
    runWriteAction { VfsUtil.saveText(psi.containingFile.virtualFile, src); }
    helper.checkModCountHasChanged(JAVA)
  }

  fun `test mod count changes twice on same content if stub index is not updated 2`() {
    val src = """
      class Predicate {
        boolean test(int x) {
          return true;
        }
      }
    """.trimIndent()
    val psi = myFixture.addFileToProject("Foo.java", src)
    helper.initModCounts(JAVA)
    helper.ensureStubIndexUpToDate(project)
    helper.checkModCountIsSame(JAVA)
    runWriteAction { VfsUtil.saveText(psi.containingFile.virtualFile, src); }
    helper.checkModCountIsSame(JAVA)

    val src2 = """
      class Predicate {
        boolean tes2525t(float x) {
          return x == 0.0f;
        }
      }
    """.trimIndent()
    runWriteAction { VfsUtil.saveText(psi.containingFile.virtualFile, src2); }
    helper.checkModCountHasChanged(JAVA)
    runWriteAction { VfsUtil.saveText(psi.containingFile.virtualFile, src2); }
    helper.checkModCountHasChanged(JAVA)
  }
}