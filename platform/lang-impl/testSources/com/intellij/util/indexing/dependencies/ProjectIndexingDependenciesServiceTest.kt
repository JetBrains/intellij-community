package com.intellij.util.indexing.dependencies

import com.intellij.mock.MockVirtualFile
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.rules.TempDirectory
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import kotlin.math.min

@RunWith(JUnit4::class)
class ProjectIndexingDependenciesServiceTest {
  private val DEFAULT_VFS_INDEXING_STAMP_VALUE: Long = 0

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
  fun `test corrupted scanning mark`() {
    val file = factory.nonExistingFile()
    FileOutputStream(file).use {
      it.write(1.asByteArray().plus(ByteArray(3))) // 4 bytes for file version + 3 bytes
    }

    try {
      factory.newProjectIndexingDependenciesService(file)
      fail("Should throw exception, because 3 bytes is too few to read indexing stamp")
    }
    catch (ae: AssertionError) {
      val expected = "Could not read incomplete scanning mark (only 3 bytes read). Storage path: "
      val actual = ae.message!!
      assertEquals(expected, actual.substring(0, min(expected.length, actual.length)))
    }
  }

  @Test
  fun `test v0 v1 migration`() {
    val file = factory.nonExistingFile()
    FileOutputStream(file).use {
      it.write(0.asByteArray().plus(1.asByteArray())) // version and incomplete scanning mark
    }

    factory.newProjectIndexingDependenciesService(file)
    val inst = factory.newProjectIndexingDependenciesService(file)
    val indexingRequestId = inst.getAppIndexingRequestIdOfLastScanning()
    assertEquals(-1, indexingRequestId)
  }

  @Test
  fun `test requestHeavyScanningOnProjectOpen in project`() {
    val file = factory.nonExistingFile()
    val inst = factory.newProjectIndexingDependenciesService(file)

    val oldStamp = inst.newScanningTokenOnProjectOpen(true)
    assertTrue(oldStamp.toString(), oldStamp is ReadWriteScanningRequestTokenImpl)

    inst.requestHeavyScanningOnProjectOpen("test requestHeavyScanningOnProjectOpen in project")

    val newStamp = inst.newScanningTokenOnProjectOpen(true)
    assertTrue(oldStamp.toString(), newStamp is WriteOnlyScanningRequestTokenImpl)
  }

  @Test
  fun `test invalidateAllStamps in app`() {
    val file = factory.nonExistingFile()
    val inst = factory.newProjectIndexingDependenciesService(file)
    val oldStamp = inst.getLatestIndexingRequestToken()
    factory.sharedAppService.invalidateAllStamps("test invalidateAllStamps in app")
    val newStamp = inst.getLatestIndexingRequestToken()

    assertNotEquals(oldStamp, newStamp)
  }

  // TODO: test heavy scanning after incomplete scanning

  @Test
  fun `test service reload keeps state`() {
    val file = factory.nonExistingFile()
    val inst1 = factory.newProjectIndexingDependenciesService(file)

    factory.sharedAppService.invalidateAllStamps("test service reload keeps state") // make some non-default sate
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

  fun Int.asByteArray(): ByteArray {
    val fourBytes = ByteBuffer.allocate(Int.SIZE_BYTES)
    fourBytes.putInt(this)
    fourBytes.rewind()
    return fourBytes.array()
  }
}