package com.intellij.util.indexing.dependencies

import com.intellij.mock.MockVirtualFile
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.rules.TempDirectory
import com.intellij.util.indexing.dependencies.ProjectIndexingDependenciesService.Companion.NULL_STAMP
import com.intellij.util.indexing.dependencies.ProjectIndexingDependenciesService.FileIndexingStampImpl
import com.intellij.util.indexing.dependencies.ProjectIndexingDependenciesService.IndexingRequestTokenImpl
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.io.File
import java.io.FileOutputStream
import kotlin.math.min

@RunWith(JUnit4::class)
class ProjectIndexingDependenciesServiceTest {
  private val DEFAULT_VFS_INDEXING_STAMP_VALUE: Int = 0

  @JvmField
  @Rule
  val appRule = ApplicationRule()

  @Rule
  @JvmField
  val temp = TempDirectory()

  private val testDisposable = Disposer.newDisposable()
  private lateinit var tmpDir: File
  private lateinit var factory: TestFactories

  @Before
  fun setup() {
    tmpDir = temp.newDirectory("fileIndexingStamp")
    factory = TestFactories(tmpDir, testDisposable, useApplication = false)
  }

  @After
  fun tearDown() {
    Disposer.dispose(testDisposable)
  }

  @Test
  fun `test no previous file`() {
    val file = factory.nonExistingFile("nonExistingFile")
    factory.newProjectIndexingDependenciesService(file)
    assertTrue(file.exists())
  }

  @Test
  fun `test corrupted file version`() {
    val file = factory.nonExistingFile()
    FileOutputStream(file).use {
      it.write(ByteArray(3)) // 3 bytes is too few to read file version
    }

    try {
      factory.newProjectIndexingDependenciesService(file)
      fail("Should throw exception, because 3 bytes is too few to read file version")
    }
    catch (ae: AssertionError) {
      val expected = "Could not read storage version (only 3 bytes read). Storage path: "
      val actual = ae.message!!
      assertEquals(expected, actual.substring(0, min(expected.length, actual.length)))
    }
  }

  @Test
  fun `test corrupted indexingRequestId`() {
    val file = factory.nonExistingFile()
    FileOutputStream(file).use {
      it.write(ByteArray(7)) // 4 bytes for file version + 3 bytes
    }

    try {
      factory.newProjectIndexingDependenciesService(file)
      fail("Should throw exception, because 3 bytes is too few to read indexing stamp")
    }
    catch (ae: AssertionError) {
      val expected = "Could not read indexing stamp (only 3 bytes read). Storage path: "
      val actual = ae.message!!
      assertEquals(expected, actual.substring(0, min(expected.length, actual.length)))
    }
  }

  @Test
  fun `test invalidateAllStamps in project`() {
    val file = factory.nonExistingFile()
    val inst = factory.newProjectIndexingDependenciesService(file)
    val oldStamp = inst.getLatestIndexingRequestToken()
    inst.invalidateAllStamps()
    val newStamp = inst.getLatestIndexingRequestToken()

    assertNotEquals(oldStamp, newStamp)
  }

  @Test
  fun `test invalidateAllStamps in project invalidates only that project`() {
    val file1 = factory.nonExistingFile("storage1")
    val file2 = factory.nonExistingFile("storage2")

    val inst1 = factory.newProjectIndexingDependenciesService(file1)
    val inst2 = factory.newProjectIndexingDependenciesService(file2)

    val oldStamp1 = inst1.getLatestIndexingRequestToken()
    val oldStamp2 = inst2.getLatestIndexingRequestToken()

    inst1.invalidateAllStamps()

    val newStamp1 = inst1.getLatestIndexingRequestToken()
    val newStamp2 = inst2.getLatestIndexingRequestToken()

    assertNotEquals(oldStamp1, newStamp1)
    assertEquals(oldStamp2, newStamp2)
  }

  @Test
  fun `test invalidateAllStamps in app`() {
    val file = factory.nonExistingFile()
    val inst = factory.newProjectIndexingDependenciesService(file)
    val oldStamp = inst.getLatestIndexingRequestToken()
    factory.sharedAppService.invalidateAllStamps()
    val newStamp = inst.getLatestIndexingRequestToken()

    assertNotEquals(oldStamp, newStamp)
  }

  @Test
  fun `test service reload keeps state`() {
    val file = factory.nonExistingFile()
    val inst1 = factory.newProjectIndexingDependenciesService(file)

    inst1.invalidateAllStamps() // make some non-default sate
    val oldStamp = inst1.getLatestIndexingRequestToken()

    val inst2 = factory.newProjectIndexingDependenciesService(file)
    val newStamp = inst2.getLatestIndexingRequestToken()

    assertEquals(oldStamp, newStamp)
  }

  @Test
  fun `test recovery after incompatible version change`() {
    val file = factory.nonExistingFile()
    FileOutputStream(file).use {
      it.write(ByteArray(4) { -1 })
    }

    try {
      factory.newProjectIndexingDependenciesService(file)
      fail("Should log.error and reset the storage")
    }
    catch (e: AssertionError) {
      val expected = "Incompatible version change"
      val actual = e.message!!.substring(0, expected.length)
      assertEquals(expected, actual)
    }

    factory.newProjectIndexingDependenciesService(file)
    // should not throw
  }

  @Test
  fun `test clean files are not marked as indexed by default (modificationStamp=0)`() {
    val indexingRequest = factory.newProjectIndexingDependenciesService().getLatestIndexingRequestToken() as IndexingRequestTokenImpl

    val fileIndexingStamp = indexingRequest.getFileIndexingStamp(0)
    assertFalse("Should not be equal to default VFS value (0): $fileIndexingStamp",
                fileIndexingStamp.isSame(DEFAULT_VFS_INDEXING_STAMP_VALUE))
  }

  @Test
  fun `test non-markable files are not marked as indexed by default (modificationStamp=-1)`() {
    val file = MockVirtualFile("test")
    val indexingRequest = factory.newProjectIndexingDependenciesService().getLatestIndexingRequestToken()

    val fileIndexingStamp = indexingRequest.getFileIndexingStamp(file)
    assertFalse("Should not be equal to default VFS value (0): $fileIndexingStamp",
                fileIndexingStamp.isSame(DEFAULT_VFS_INDEXING_STAMP_VALUE))
  }

  @Test
  fun `test modificationStamp change invalidates indexing flag`() {
    val indexingRequest = factory.newProjectIndexingDependenciesService().getLatestIndexingRequestToken() as IndexingRequestTokenImpl
    val fileIndexingStampBefore = indexingRequest.getFileIndexingStamp(42)
    val fileIndexingStampAfter = indexingRequest.getFileIndexingStamp(43)
    assertNotEquals(fileIndexingStampBefore, fileIndexingStampAfter)
  }

  @Test
  fun `test NULL_STAMP is not equal to any other int, not even to NULL_INDEXING_STAMP`() {
    assertFalse(NULL_STAMP.isSame(0))
    assertFalse(NULL_STAMP.isSame(42))

    assertFalse(FileIndexingStampImpl(0).isSame(0))
    assertFalse(FileIndexingStampImpl(0).isSame(42))
    assertFalse(FileIndexingStampImpl(42).isSame(0))
    assertTrue(FileIndexingStampImpl(42).isSame(42))

    assertEquals(FileIndexingStampImpl(0), FileIndexingStampImpl(0))
    assertEquals(FileIndexingStampImpl(42), FileIndexingStampImpl(42))
    assertNotEquals(FileIndexingStampImpl(41), FileIndexingStampImpl(42))

    assertNotEquals(NULL_STAMP, FileIndexingStampImpl(0))
    assertNotEquals(FileIndexingStampImpl(0), NULL_STAMP)
    assertNotEquals(NULL_STAMP, FileIndexingStampImpl(42))
    assertNotEquals(FileIndexingStampImpl(42), NULL_STAMP)
  }
}