// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.util.indexing

import com.intellij.ide.highlighter.JavaFileType
import com.intellij.lang.java.JavaParserDefinition
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.application.readAction
import com.intellij.openapi.fileEditor.impl.LoadTextUtil
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.psi.tree.IFileElementType
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.javaModuleCodeInsightFixture
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@TestApplication
class JavaPerFileElementTypeModificationTrackerTest {
  private val myFixture by javaModuleCodeInsightFixture(PathManager.getHomePath())
  private val project get() = myFixture.project

  companion object {
    val JAVA: IFileElementType = JavaParserDefinition.JAVA_FILE!!
  }

  private val helper = StubIndexPerFileElementTypeModificationTrackerTestHelper()

  @BeforeEach
  fun setUp() {
    helper.setUp()
  }

  @Test
  fun `test mod count increments on java file creation and change`(): Unit = timeoutRunBlocking {
    helper.initModCounts(JAVA)
    val psi = myFixture.addFileToProject("Foo.java", "class Foo { String bar; }")
    helper.checkModCountHasChanged(JAVA)
    readAction { helper.ensureStubIndexUpToDate(project) }
    edtWriteAction { VfsUtil.saveText(psi.containingFile.virtualFile, "class Foo { int val; }"); }
    helper.checkModCountHasChanged(JAVA)
  }

  @Test
  fun `test mod count doesnt change on non-stub changes`(): Unit = timeoutRunBlocking {
    helper.initModCounts(JAVA)
    val psi = myFixture.addFileToProject("Foo.java", """
      class Predicate {
        boolean test(int x) {
          return true;
        }
      }
    """.trimIndent())
    helper.checkModCountHasChanged(JAVA)
    readAction { helper.ensureStubIndexUpToDate(project) }
    edtWriteAction { VfsUtil.saveText(psi.containingFile.virtualFile, """
      class Predicate {
        boolean test(int x) {
          return x >= 0;
        }
      }
    """.trimIndent()); }
    helper.checkModCountIsSame(JAVA)
  }

  @Test
  fun `test mod count changes twice on same content if stub index is not updated`(): Unit = timeoutRunBlocking {
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
    edtWriteAction { VfsUtil.saveText(psi.containingFile.virtualFile, src); }
    helper.checkModCountHasChanged(JAVA)
  }

  @Test
  fun `test mod count changes twice on same content if stub index is not updated 2`(): Unit = timeoutRunBlocking {
    val src = """
      class Predicate {
        boolean test(int x) {
          return true;
        }
      }
    """.trimIndent()
    val psi = myFixture.addFileToProject("Foo.java", src)
    helper.initModCounts(JAVA)
    readAction { helper.ensureStubIndexUpToDate(project) }
    helper.checkModCountIsSame(JAVA)
    edtWriteAction { VfsUtil.saveText(psi.containingFile.virtualFile, src); }
    helper.checkModCountIsSame(JAVA)

    val src2 = """
      class Predicate {
        boolean tes2525t(float x) {
          return x == 0.0f;
        }
      }
    """.trimIndent()
    edtWriteAction { VfsUtil.saveText(psi.containingFile.virtualFile, src2); }
    helper.checkModCountHasChanged(JAVA)
    edtWriteAction { VfsUtil.saveText(psi.containingFile.virtualFile, src2); }
    helper.checkModCountHasChanged(JAVA)
  }

  @Test
  fun `test tracker does not modify file line separator data`(): Unit = timeoutRunBlocking {
    val psi = myFixture.configureByText(JavaFileType.INSTANCE, """
      class Predicate {
        boolean test(int x) {
          return true;
        }
        <caret>
      }
    """.trimIndent())
    helper.initModCounts(JAVA)
    readAction { helper.ensureStubIndexUpToDate(project) }
    edtWriteAction { LoadTextUtil.changeLineSeparators(project, psi.virtualFile, "\r\n", this) }
    helper.checkModCountIsSame(JAVA)
    assertEquals("\r\n", psi.virtualFile.detectedLineSeparator)
    myFixture.type("int smth() { return 239; }\n")
    helper.checkModCountHasChanged(JAVA)
    assertEquals("\r\n", psi.virtualFile.detectedLineSeparator)
  }
}