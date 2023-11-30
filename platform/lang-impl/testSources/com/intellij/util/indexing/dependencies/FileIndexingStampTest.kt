// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.dependencies

import org.junit.Assert
import org.junit.Test

class FileIndexingStampTest {
  @Test
  fun `test WriteOnlyFileIndexingStampImpl is not equal to any other int`() {
    Assert.assertFalse(WriteOnlyFileIndexingStampImpl(0).isSame(0))
    Assert.assertFalse(WriteOnlyFileIndexingStampImpl(0).isSame(42))
    Assert.assertFalse(WriteOnlyFileIndexingStampImpl(42).isSame(0))
    Assert.assertFalse(WriteOnlyFileIndexingStampImpl(42).isSame(42))

    Assert.assertEquals(WriteOnlyFileIndexingStampImpl(0), WriteOnlyFileIndexingStampImpl(0))
    Assert.assertEquals(WriteOnlyFileIndexingStampImpl(42), WriteOnlyFileIndexingStampImpl(42))
    Assert.assertNotEquals(WriteOnlyFileIndexingStampImpl(41), WriteOnlyFileIndexingStampImpl(42))
  }

  @Test
  fun `test ReadWriteFileIndexingStampImpl`() {
    Assert.assertFalse(ReadWriteFileIndexingStampImpl(0).isSame(0))
    Assert.assertFalse(ReadWriteFileIndexingStampImpl(0).isSame(42))
    Assert.assertFalse(ReadWriteFileIndexingStampImpl(42).isSame(0))
    Assert.assertTrue(ReadWriteFileIndexingStampImpl(42).isSame(42))

    Assert.assertEquals(ReadWriteFileIndexingStampImpl(0), ReadWriteFileIndexingStampImpl(0))
    Assert.assertEquals(ReadWriteFileIndexingStampImpl(42), ReadWriteFileIndexingStampImpl(42))
    Assert.assertNotEquals(ReadWriteFileIndexingStampImpl(41), ReadWriteFileIndexingStampImpl(42))
  }

  @Test
  fun `test NULL_STAMP is not equal to any other int, not even to NULL_INDEXING_STAMP`() {
    Assert.assertFalse(ProjectIndexingDependenciesService.NULL_STAMP.isSame(0))
    Assert.assertFalse(ProjectIndexingDependenciesService.NULL_STAMP.isSame(42))

    Assert.assertNotEquals(ProjectIndexingDependenciesService.NULL_STAMP, ReadWriteFileIndexingStampImpl(0))
    Assert.assertNotEquals(ProjectIndexingDependenciesService.NULL_STAMP, WriteOnlyFileIndexingStampImpl(0))
    Assert.assertNotEquals(ReadWriteFileIndexingStampImpl(0), ProjectIndexingDependenciesService.NULL_STAMP)
    Assert.assertNotEquals(WriteOnlyFileIndexingStampImpl(0), ProjectIndexingDependenciesService.NULL_STAMP)
    Assert.assertNotEquals(ProjectIndexingDependenciesService.NULL_STAMP, ReadWriteFileIndexingStampImpl(42))
    Assert.assertNotEquals(ReadWriteFileIndexingStampImpl(42), ProjectIndexingDependenciesService.NULL_STAMP)
    Assert.assertNotEquals(WriteOnlyFileIndexingStampImpl(42), ProjectIndexingDependenciesService.NULL_STAMP)
  }
}