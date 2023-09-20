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
  private lateinit var appService: AppIndexingDependenciesService
  private lateinit var tmpDir: File

  @Before
  fun setup() {
    tmpDir = temp.newDirectory("fileIndexingStamp")
    appService = newAppIndexingDependenciesService(nonExistingFile("app"))
  }

  @After
  fun tearDown() {
    Disposer.dispose(testDisposable)
  }

  @Test
  fun `test no previous file`() {
    val file = nonExistingFile("nonExistingFile")
    newProjectIndexingDependenciesService(file)
    assertTrue(file.exists())
  }

  @Test
  fun `test corrupted file version`() {
    val file = nonExistingFile()
    FileOutputStream(file).use {
      it.write(ByteArray(3)) // 3 bytes is too few to read file version
    }

    try {
      newProjectIndexingDependenciesService(file)
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
    val file = nonExistingFile()
    FileOutputStream(file).use {
      it.write(ByteArray(7)) // 4 bytes for file version + 3 bytes
    }

    try {
      newProjectIndexingDependenciesService(file)
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
    val file = nonExistingFile()
    val inst = newProjectIndexingDependenciesService(file)
    val oldStamp = inst.getLatestIndexingRequestToken()
    inst.invalidateAllStamps()
    val newStamp = inst.getLatestIndexingRequestToken()

    assertNotEquals(oldStamp, newStamp)
  }

  @Test
  fun `test invalidateAllStamps in project invalidates only that project`() {
    val file1 = nonExistingFile("storage1")
    val file2 = nonExistingFile("storage2")

    val inst1 = newProjectIndexingDependenciesService(file1)
    val inst2 = newProjectIndexingDependenciesService(file2)

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
    val file = nonExistingFile()
    val inst = newProjectIndexingDependenciesService(file)
    val oldStamp = inst.getLatestIndexingRequestToken()
    appService.invalidateAllStamps()
    val newStamp = inst.getLatestIndexingRequestToken()

    assertNotEquals(oldStamp, newStamp)
  }

  @Test
  fun `test service reload keeps state`() {
    val file = nonExistingFile()
    val inst1 = newProjectIndexingDependenciesService(file)

    inst1.invalidateAllStamps() // make some non-default sate
    val oldStamp = inst1.getLatestIndexingRequestToken()

    val inst2 = newProjectIndexingDependenciesService(file)
    val newStamp = inst2.getLatestIndexingRequestToken()

    assertEquals(oldStamp, newStamp)
  }

  private fun nonExistingFile(s: String = "storage"): File {
    val file = tmpDir.resolve(s)
    assertFalse(file.exists())
    return file
  }

  private fun newAppIndexingDependenciesService(file: File): AppIndexingDependenciesService {
    return AppIndexingDependenciesService(file.toPath()).also {
      Disposer.register(testDisposable, it)
      assertTrue(file.exists())
    }
  }

  private fun newProjectIndexingDependenciesService(file: File): ProjectIndexingDependenciesService {
    return newProjectIndexingDependenciesService(file, appService)
  }

  private fun newProjectIndexingDependenciesService(file: File,
                                                    appService: AppIndexingDependenciesService): ProjectIndexingDependenciesService {
    return ProjectIndexingDependenciesService(file.toPath(), appService).also {
      Disposer.register(testDisposable, it)
      assertTrue(file.exists())
    }
  }
}