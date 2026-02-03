package com.intellij.util.indexing.dependencies

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
import java.io.FileOutputStream
import kotlin.math.min

@RunWith(JUnit4::class)
class AppIndexingDependenciesServiceTest {

  @JvmField
  @Rule
  val appRule = ApplicationRule()

  @Rule
  @JvmField
  val temp = TempDirectory()

  private val testDisposable = Disposer.newDisposable()
  private lateinit var factory: TestFactories

  @Before
  fun setup() {
    val tmpDir = temp.newDirectory("fileIndexingStamp")
    factory = TestFactories(tmpDir, testDisposable, useApplication = false)
  }

  @After
  fun tearDown() {
    Disposer.dispose(testDisposable)
  }

  @Test
  fun `test no previous file`() {
    val file = factory.nonExistingFile("nonExistingFile")
    factory.newAppIndexingDependenciesService(file)
    assertTrue(file.exists())
  }

  @Test
  fun `test corrupted file version`() {
    val file = factory.nonExistingFile()
    FileOutputStream(file).use {
      it.write(ByteArray(3)) // 3 bytes is too few to read file version
    }

    try {
      factory.newAppIndexingDependenciesService(file)
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
    val file = factory.nonExistingFile()
    FileOutputStream(file).use {
      // 4 bytes for file version + 3 bytes
      val bytes = byteArrayOf(0, 0, 0, 1) + byteArrayOf(0, 0, 0)
      it.write(bytes)
    }

    try {
      factory.newAppIndexingDependenciesService(file)
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
    val inst = factory.newAppIndexingDependenciesService()
    val oldStamp = inst.getCurrentTokenInTest()
    inst.invalidateAllStamps("test invalidateAllStamps")
    val newStamp = inst.getCurrentTokenInTest()

    assertNotEquals(oldStamp, newStamp)
  }

  @Test
  fun `test service reload keeps state`() {
    val file = factory.nonExistingFile()
    val inst1 = factory.newAppIndexingDependenciesService(file)

    inst1.invalidateAllStamps("test service reload keeps state") // make some non-default sate
    val oldStamp = inst1.getCurrentTokenInTest()

    val inst2 = factory.newAppIndexingDependenciesService(file)
    val newStamp = inst2.getCurrentTokenInTest()

    assertEquals(oldStamp, newStamp)
  }

  @Test
  fun `test recovery after incompatible version change`() {
    val file = factory.nonExistingFile()
    FileOutputStream(file).use {
      it.write(ByteArray(4) { -1 })
    }

    try {
      factory.newAppIndexingDependenciesService(file)
      fail("Should log.error and reset the storage")
    }
    catch (e: AssertionError) {
      val expected = "Incompatible version change"
      val actual = e.message!!.substring(0, expected.length)
      assertEquals(expected, actual)
    }

    factory.newAppIndexingDependenciesService(file)
    // should not throw
  }
}