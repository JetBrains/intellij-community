package com.intellij.util.indexing.dependencies

import com.intellij.openapi.util.Disposer
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
import kotlin.math.min

@RunWith(JUnit4::class)
class ProjectIndexingDependenciesServiceTest {
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
}