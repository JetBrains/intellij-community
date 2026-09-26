// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.util.gotoByName

import com.intellij.openapi.fileTypes.FileType
import com.intellij.util.ui.EmptyIcon
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger
import javax.swing.Icon

/**
 * Guards against IJPL-243365: building [FileTypeRef]s for the Search Everywhere / Goto File
 * file-type filter must not eagerly call [FileType.getIcon], which triggers expensive
 * classloading of plugin icon holders on the EDT.
 */
class FileTypeRefLazyIconTest {
  private class CountingFileType(private val iconCalls: AtomicInteger) : FileType {
    override fun getName(): String = "FAKE_FILE_TYPE_FOR_TEST"
    override fun getDescription(): String = "fake"
    override fun getDefaultExtension(): String = ""
    override fun isBinary(): Boolean = false
    override fun getIcon(): Icon {
      iconCalls.incrementAndGet()
      return EmptyIcon.ICON_16
    }
  }

  @Test
  fun `forFileType does not call getIcon eagerly`() {
    val calls = AtomicInteger()
    val ref = FileTypeRef.forFileType(CountingFileType(calls))
    assertEquals(0, calls.get(), "getIcon must not be called while constructing a FileTypeRef")
    assertEquals("FAKE_FILE_TYPE_FOR_TEST", ref.name)
    assertEquals("FAKE_FILE_TYPE_FOR_TEST", ref.displayName)
  }

  @Test
  fun `icon is computed lazily on first access and memoized`() {
    val calls = AtomicInteger()
    val ref = FileTypeRef.forFileType(CountingFileType(calls))
    assertNotNull(ref.icon)
    ref.icon
    ref.icon
    assertEquals(1, calls.get(), "getIcon must be invoked exactly once and the result memoized")
  }

  @Test
  fun `equals and hashCode depend on name only`() {
    val a = FileTypeRef("X", "display A", null)
    val b = FileTypeRef("X", "display B", EmptyIcon.ICON_16)
    assertEquals(a, b)
    assertEquals(a.hashCode(), b.hashCode())
  }

  @Test
  fun `forFileType interns one ref per file type and computes the icon at most once`() {
    val calls = AtomicInteger()
    val fileType = CountingFileType(calls)
    val first = FileTypeRef.forFileType(fileType)
    val second = FileTypeRef.forFileType(fileType)
    assertSame(first, second, "forFileType must return the interned ref for the same file type")
    first.icon
    second.icon
    assertEquals(1, calls.get(), "the interned ref must compute its icon at most once across calls")
  }
}
