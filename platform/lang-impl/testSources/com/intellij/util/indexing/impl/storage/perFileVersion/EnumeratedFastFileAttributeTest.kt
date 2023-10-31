// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.impl.storage.perFileVersion

import com.google.common.io.Closer
import com.intellij.openapi.vfs.VirtualFileWithId
import com.intellij.openapi.vfs.newvfs.FileAttribute
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.rules.TempDirectory
import com.intellij.util.indexing.impl.perFileVersion.EnumeratedFastFileAttribute
import com.intellij.util.io.KeyDescriptor
import com.intellij.util.io.PersistentEnumerator
import com.intellij.util.io.delete
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.io.DataInput
import java.io.DataOutput
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger
import kotlin.io.path.exists
import kotlin.io.path.isDirectory

private data class EnumeratedTestClass(val testIntField: Int)

private class EnumeratedTestClassDescriptor() : KeyDescriptor<EnumeratedTestClass> {
  override fun isEqual(val1: EnumeratedTestClass?, val2: EnumeratedTestClass?): Boolean = val1 == val2

  override fun getHashCode(value: EnumeratedTestClass?): Int = value?.testIntField ?: 0

  override fun save(out: DataOutput, value: EnumeratedTestClass?) {
    if (value == null) {
      out.writeLong(Long.MIN_VALUE)
    }
    else {
      out.writeLong(value.testIntField.toLong())
    }
  }

  override fun read(inp: DataInput): EnumeratedTestClass? {
    val readLong = inp.readLong()
    return if (readLong == Long.MIN_VALUE) {
      null
    }
    else {
      EnumeratedTestClass(readLong.toInt())
    }
  }
}

@RunWith(JUnit4::class)
class EnumeratedFastFileAttributeTest {
  companion object {
    private val fileAttrCounter: AtomicInteger = AtomicInteger()
  }

  @JvmField
  @Rule
  val appRule = ApplicationRule()

  @Rule
  @JvmField
  val temp = TempDirectory()

  private val closer: Closer = Closer.create()

  @After
  fun tearDown() {
    closer.close()
  }

  private fun createFileAttribute() = FileAttribute("EnumeratedFastFileAttributeTest.${fileAttrCounter.getAndIncrement()}", 0, true)

  private fun createEnumeratedFastFileAttribute(dir: Path): EnumeratedFastFileAttribute<EnumeratedTestClass> =
    EnumeratedFastFileAttribute(dir, createFileAttribute(), EnumeratedTestClassDescriptor()) {
      PersistentEnumerator(it, EnumeratedTestClassDescriptor(), 32)
    }.also(closer::register)

  private fun createEnumeratedFastFileAttribute(dir: Path, vfsVersion: Long): EnumeratedFastFileAttribute<EnumeratedTestClass> =
    EnumeratedFastFileAttribute(dir, createFileAttribute(), EnumeratedTestClassDescriptor(), vfsVersion) {
      PersistentEnumerator(it, EnumeratedTestClassDescriptor(), 32)
    }.also(closer::register)

  @Test
  fun testDirCreatedAutomatically() {
    val dir = temp.newDirectory().toPath().resolve("a/b")
    assertFalse(dir.exists())
    createEnumeratedFastFileAttribute(dir).use {
      assertTrue(dir.isDirectory())
    }
  }

  @Test
  fun testOpenWriteReadClose() {
    val dir = temp.newDirectory().toPath()
    val f = temp.newVirtualFile("test", "content".toByteArray()) as VirtualFileWithId
    val w = EnumeratedTestClass(1)

    createEnumeratedFastFileAttribute(dir).use { storage ->
      storage.writeEnumerated(f.id, w)
      val r = storage.readEnumerated(f.id)
      assertEquals(w, r)
    }
  }

  @Test
  fun testOpenWriteClose_OpenReadClose() {
    val dir = temp.newDirectory().toPath()
    val f = temp.newVirtualFile("test", "content".toByteArray()) as VirtualFileWithId
    val w = EnumeratedTestClass(1)

    createEnumeratedFastFileAttribute(dir).use { storage ->
      storage.writeEnumerated(f.id, w)
    }

    createEnumeratedFastFileAttribute(dir).use { storage ->
      val r = storage.readEnumerated(f.id)
      assertEquals(w, r)
    }
  }

  @Test
  fun testOpenWriteClose_Delete_OpenReadClose() {
    val dir = temp.newDirectory().toPath()
    val f = temp.newVirtualFile("test", "content".toByteArray()) as VirtualFileWithId
    val w = EnumeratedTestClass(1)

    createEnumeratedFastFileAttribute(dir).use { storage ->
      storage.writeEnumerated(f.id, w)
    }

    dir.delete(recursively = true)

    createEnumeratedFastFileAttribute(dir).use { storage ->
      val r = storage.readEnumerated(f.id)
      assertNull(r)
    }
  }

  @Test
  fun testOpenWriteClose_VfsChange_OpenReadWriteClose_OpenReadClose() {
    val dir = temp.newDirectory().toPath()
    val f = temp.newVirtualFile("test", "content".toByteArray()) as VirtualFileWithId
    val w = EnumeratedTestClass(1)

    createEnumeratedFastFileAttribute(dir, vfsVersion = 1).use { storage ->
      storage.writeEnumerated(f.id, w)
    }

    createEnumeratedFastFileAttribute(dir, vfsVersion = 2).use { storage ->
      val r = storage.readEnumerated(f.id)
      assertNull(r)
      storage.writeEnumerated(f.id, w)
    }

    createEnumeratedFastFileAttribute(dir, vfsVersion = 2).use { storage ->
      val r = storage.readEnumerated(f.id)
      assertEquals(w, r)
    }
  }

  @Test
  fun testOpenWriteClose_VfsChange_OpenReadWriteClose_VfsChangeBack_OpenReadClose() {
    val dir = temp.newDirectory().toPath()
    val f = temp.newVirtualFile("test", "content".toByteArray()) as VirtualFileWithId
    val w = EnumeratedTestClass(1)

    createEnumeratedFastFileAttribute(dir, vfsVersion = 1).use { storage ->
      storage.writeEnumerated(f.id, w)
    }

    createEnumeratedFastFileAttribute(dir, vfsVersion = 2).use { storage ->
      val r = storage.readEnumerated(f.id)
      assertNull(r)
      storage.writeEnumerated(f.id, w)
    }

    createEnumeratedFastFileAttribute(dir, vfsVersion = 1).use { storage ->
      val r = storage.readEnumerated(f.id)
      assertNull(r)
    }
  }
}