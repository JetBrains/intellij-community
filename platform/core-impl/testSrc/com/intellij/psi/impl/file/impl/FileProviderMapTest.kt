// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.file.impl

import com.intellij.codeInsight.multiverse.CodeInsightContext
import com.intellij.codeInsight.multiverse.anyContext
import com.intellij.lang.Language
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiReference
import com.intellij.util.ref.GCWatcher
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNull

/**
 * Tests the contract of [FileProviderMap] that needs no project.
 *
 * The promotion of an [anyContext] entry to a specific context is not here. It calls
 * `CodeInsightContextManagerImpl.getInstanceImpl(viewProvider.manager.project)`, so it needs a project.
 * `FileContextTest.testAnyContextIsPromotedToExactContext` covers it.
 */
class FileProviderMapTest {

  @Test
  fun `empty map returns no provider`() {
    val map = FileProviderMap()

    assertNull(map[anyContext()])
    assertNull(map[MockContext("context1")])
    assertEquals(emptyList<Map.Entry<CodeInsightContext, FileViewProvider>>(), map.entries.toList())
  }

  @Test
  fun `any context is stored as is in an empty map`() {
    val map = FileProviderMap()
    val provider = MockFileViewProvider("provider1")

    assertSame(provider, map.cacheOrGet(anyContext(), provider))

    assertSame(provider, map[anyContext()])
    val entries = map.entries.toList()
    assertEquals(1, entries.size)
    assertEquals(anyContext(), entries.single().key)
  }

  @Test
  fun `any context returns the same provider on every request`() {
    val context1 = MockContext("context1")
    val context2 = MockContext("context2")
    val provider1 = MockFileViewProvider("provider1")
    val provider2 = MockFileViewProvider("provider2")

    val map = FileProviderMap()
    map.cacheOrGet(context1, provider1)

    val found = map[anyContext()]
    assertSame(provider1, found)
    assertSame(found, map[anyContext()])

    // a second specific context must not move the any-context answer
    map.cacheOrGet(context2, provider2)
    assertSame(found, map[anyContext()])
    assertSame(found, map[anyContext()])
  }

  @Test
  fun `removeAllAndSetAny replaces every entry`() {
    val map = FileProviderMap()
    map.cacheOrGet(MockContext("context1"), MockFileViewProvider("provider1"))
    map.cacheOrGet(MockContext("context2"), MockFileViewProvider("provider2"))
    map.cacheOrGet(MockContext("context3"), MockFileViewProvider("provider3"))
    assertEquals(3, map.entries.size)

    val provider4 = MockFileViewProvider("provider4")
    map.removeAllAndSetAny(provider4)

    val entries = map.entries.toList()
    assertEquals(1, entries.size)
    assertEquals(anyContext(), entries.single().key)
    assertSame(provider4, entries.single().value)
    assertSame(map, provider4.getFileProviderMap())
  }

  @Test
  fun `remove needs the same provider instance`() {
    val context1 = MockContext("context1")
    val context2 = MockContext("context2")
    val provider1 = MockFileViewProvider("provider1")
    val provider2 = MockFileViewProvider("provider2")

    val map = FileProviderMap()
    map.cacheOrGet(context1, provider1)
    map.cacheOrGet(context2, provider2)

    assertFalse(map.remove(context1, provider2))
    assertSame(provider1, map[context1])

    // context1 holds the default value of the map, so this also reassigns it
    assertTrue(map.remove(context1, provider1))
    assertNull(map[context1])
    assertSame(provider2, map[anyContext()])
  }

  @Test
  fun `any context is reassigned when its provider is collected`() {
    val context1 = MockContext("context1")
    val provider2 = MockFileViewProvider("provider2")
    val provider3 = MockFileViewProvider("provider3")

    // provider1 is not referenced from anywhere else, so it should be collected
    fun initMap(): Pair<FileProviderMap, GCWatcher> {
      val provider1 = MockFileViewProvider("provider1")

      val map = FileProviderMap()
      map.cacheOrGet(context1, provider1)
      map.cacheOrGet(MockContext("context2"), provider2)
      map.cacheOrGet(MockContext("context3"), provider3)

      // the first added entry owns the default value
      assertSame(provider1, map[anyContext()])

      return map to GCWatcher.tracking(provider1)
    }

    val (map, provider1Tracker) = initMap()

    provider1Tracker.ensureCollected()

    val found = map[anyContext()]
    assertTrue(found === provider2 || found === provider3) { "unexpected provider: $found" }
    assertEquals(2, map.entries.size)
  }

  @Test
  fun `any context returns nothing when every provider is collected`() {
    // no provider is referenced from anywhere else, so all of them should be collected
    fun initMap(): Pair<FileProviderMap, List<GCWatcher>> {
      val provider1 = MockFileViewProvider("provider1")
      val provider2 = MockFileViewProvider("provider2")

      val map = FileProviderMap()
      map.cacheOrGet(MockContext("context1"), provider1)
      map.cacheOrGet(MockContext("context2"), provider2)

      return map to listOf(GCWatcher.tracking(provider1), GCWatcher.tracking(provider2))
    }

    val (map, trackers) = initMap()

    for (tracker in trackers) {
      tracker.ensureCollected()
    }

    assertNull(map[anyContext()])
    assertEquals(emptyList<Map.Entry<CodeInsightContext, FileViewProvider>>(), map.entries.toList())
  }
}

/**
 * A [FileViewProvider] that only holds user data. [FileProviderMap] stores a strong link to itself in the
 * user data of a provider, and reads nothing else from a provider unless it installs a context.
 */
// The signatures copy FileViewProvider, so they keep its nullability and its deprecated members.
@Suppress("RedundantNullableReturnType", "OVERRIDE_DEPRECATION")
private class MockFileViewProvider(private val name: String) : UserDataHolderBase(), FileViewProvider {
  override fun toString(): String = "MockFileViewProvider($name)"

  override fun getManager(): PsiManager = unsupported()
  override fun getDocument(): Document = unsupported()
  override fun getContents(): CharSequence = unsupported()
  override fun getVirtualFile(): VirtualFile = unsupported()
  override fun getBaseLanguage(): Language = unsupported()
  override fun getLanguages(): Set<Language> = unsupported()
  override fun getPsi(target: Language): PsiFile? = unsupported()
  override fun getAllFiles(): List<PsiFile> = unsupported()
  override fun isEventSystemEnabled(): Boolean = unsupported()
  override fun isPhysical(): Boolean = unsupported()
  override fun getModificationStamp(): Long = unsupported()
  override fun supportsIncrementalReparse(rootLanguage: Language): Boolean = unsupported()
  override fun rootChanged(psiFile: PsiFile): Unit = unsupported()
  override fun beforeContentsSynchronized(): Unit = unsupported()
  override fun contentsSynchronized(): Unit = unsupported()
  override fun clone(): FileViewProvider = unsupported()
  override fun findElementAt(offset: Int): PsiElement? = unsupported()
  override fun findReferenceAt(offset: Int): PsiReference? = unsupported()
  override fun findElementAt(offset: Int, language: Language): PsiElement? = unsupported()
  override fun findElementAt(offset: Int, lang: Class<out Language>): PsiElement? = unsupported()
  override fun findReferenceAt(offset: Int, language: Language): PsiReference? = unsupported()
  override fun createCopy(copy: VirtualFile): FileViewProvider = unsupported()
  override fun getStubBindingRoot(): PsiFile = unsupported()
  override fun getFileType(): FileType = unsupported()

  private fun unsupported(): Nothing = throw UnsupportedOperationException("$this is a stub")
}
