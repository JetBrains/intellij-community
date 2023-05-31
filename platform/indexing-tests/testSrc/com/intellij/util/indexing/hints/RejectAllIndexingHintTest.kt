package com.intellij.util.indexing.hints

import com.intellij.openapi.fileTypes.PlainTextFileType
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.TemporaryDirectory
import com.intellij.util.ThreeState
import com.intellij.util.indexing.IndexedFileImpl
import org.junit.Assert
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test

class RejectAllIndexingHintTest {
  @JvmField
  @Rule
  val tempDir = TemporaryDirectory()

  companion object {
    @ClassRule
    @JvmField
    val p: ProjectRule = ProjectRule(true, false, null)
  }

  @Test
  fun testAcceptFileType() {
    val hint = RejectAllIndexingHint()
    Assert.assertEquals(ThreeState.NO, hint.acceptFileType(PlainTextFileType.INSTANCE))
  }

  @Test
  fun testHitFallbackForDirectory() {
    val hint = RejectAllIndexingHint()
    val someDir = VfsUtil.findFile(tempDir.createDir(), true)!!
    Assert.assertFalse(hint.whenAllOtherHintsUnsure(IndexedFileImpl(someDir, p.project)))
  }
}