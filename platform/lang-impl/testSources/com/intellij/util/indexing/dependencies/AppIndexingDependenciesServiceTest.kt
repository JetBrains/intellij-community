package com.intellij.util.indexing.dependencies

import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.rules.TempDirectory
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.io.File
import java.io.FileOutputStream
import kotlin.math.min

@RunWith(JUnit4::class)
class AppIndexingDependenciesServiceTest {
  @Rule
  @JvmField
  val temp = TempDirectory()

  private val testDisposable = Disposer.newDisposable()

  @After
  fun tearDown() {
    Disposer.dispose(testDisposable)
  }

  @Test
  fun `test no previous file`() {
    val file = nonExistingFile("nonExistingFile")
    newAppIndexingDependenciesService(file)
    assertTrue(file.exists())
  }

  @Test
  fun `test corrupted file version`() {
    val file = nonExistingFile()
    FileOutputStream(file).use {
      it.write(ByteArray(3)) // 3 bytes is too few to read file version
    }

    try {
      newAppIndexingDependenciesService(file)
      fail("Should throw exception, because 3 bytes is too few to read file version")
    }
    catch (ae: AssertionError) {
      val expected = "Could not read storage version (only 3 bytes read). Storage path: "
      val actual = ae.message!!
      assertEquals(expected, actual.substring(0, min(expected.length, actual.length)))
    }
  }

  @Test
  fun `test corrupted fileIndexingStamp`() {
    val file = nonExistingFile()
    FileOutputStream(file).use {
      it.write(ByteArray(7)) // 4 bytes for file version + 3 bytes
    }

    try {
      newAppIndexingDependenciesService(file)
      fail("Should throw exception, because 3 bytes is too few to read indexing stamp")
    }
    catch (ae: AssertionError) {
      val expected = "Could not read indexing stamp (only 3 bytes read). Storage path: "
      val actual = ae.message!!
      assertEquals(expected, actual.substring(0, min(expected.length, actual.length)))
    }
  }

  @Test
  fun `test invalidateAllStamps`() {
    val file = nonExistingFile()
    val inst = newAppIndexingDependenciesService(file)
    val oldStamp = inst.getCurrentTokenInTest()
    inst.invalidateAllStamps()
    val newStamp = inst.getCurrentTokenInTest()

    assertNotEquals(oldStamp, newStamp)
  }

  @Test
  fun `test service reload keeps state`() {
    val file = nonExistingFile()
    val inst1 = newAppIndexingDependenciesService(file)

    inst1.invalidateAllStamps() // make some non-default sate
    val oldStamp = inst1.getCurrentTokenInTest()

    val inst2 = newAppIndexingDependenciesService(file)
    val newStamp = inst2.getCurrentTokenInTest()

    assertEquals(oldStamp, newStamp)
  }

  private fun nonExistingFile(s: String = "storage"): File {
    val tmp = temp.newDirectory("fileIndexingStamp")
    val file = tmp.resolve(s)
    assertFalse(file.exists())
    return file
  }

  private fun newAppIndexingDependenciesService(file: File): AppIndexingDependenciesService {
    return AppIndexingDependenciesService(file.toPath()).also {
      Disposer.register(testDisposable, it)
      assertTrue(file.exists())
    }
  }
}