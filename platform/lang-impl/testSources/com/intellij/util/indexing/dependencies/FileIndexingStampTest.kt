// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.dependencies

import org.assertj.core.api.Assertions.assertThat
import org.junit.Assert
import org.junit.Test

class FileIndexingStampTest {
  @Test
  fun `test WriteOnlyFileIndexingStampImpl is not equal to any other int`() {
    assertThat(WriteOnlyFileIndexingStampImpl(0).isFileChanged(0)).isEqualTo(IsFileChangedResult.UNKNOWN)
    assertThat(WriteOnlyFileIndexingStampImpl(0, true).isFileChanged(0)).isEqualTo(IsFileChangedResult.NO)
    assertThat(WriteOnlyFileIndexingStampImpl(0, true).isFileChanged(42)).isEqualTo(IsFileChangedResult.YES)
    assertThat(WriteOnlyFileIndexingStampImpl(42.withIndexingRequestId(0), true).isFileChanged(0)).isEqualTo(IsFileChangedResult.YES)
    assertThat(WriteOnlyFileIndexingStampImpl(0.withIndexingRequestId(42), true).isFileChanged(0)).isEqualTo(IsFileChangedResult.NO)

    Assert.assertEquals(WriteOnlyFileIndexingStampImpl(0), WriteOnlyFileIndexingStampImpl(0))
    Assert.assertEquals(WriteOnlyFileIndexingStampImpl(42), WriteOnlyFileIndexingStampImpl(42))
    Assert.assertNotEquals(WriteOnlyFileIndexingStampImpl(41), WriteOnlyFileIndexingStampImpl(42))
  }

  @Test
  fun `test ReadWriteFileIndexingStampImpl`() {
    assertThat(ReadWriteFileIndexingStampImpl(0).isFileChanged(0)).isEqualTo(IsFileChangedResult.UNKNOWN)
    assertThat(ReadWriteFileIndexingStampImpl(0, true).isFileChanged(0)).isEqualTo(IsFileChangedResult.NO)
    assertThat(ReadWriteFileIndexingStampImpl(0, true).isFileChanged(42)).isEqualTo(IsFileChangedResult.YES)
    assertThat(ReadWriteFileIndexingStampImpl(42.withIndexingRequestId(0), true).isFileChanged(0)).isEqualTo(IsFileChangedResult.YES)
    assertThat(ReadWriteFileIndexingStampImpl(0.withIndexingRequestId(42), true).isFileChanged(0)).isEqualTo(IsFileChangedResult.NO)

    Assert.assertEquals(ReadWriteFileIndexingStampImpl(0), ReadWriteFileIndexingStampImpl(0));
    Assert.assertEquals(ReadWriteFileIndexingStampImpl(42), ReadWriteFileIndexingStampImpl(42))
    Assert.assertNotEquals(ReadWriteFileIndexingStampImpl(41), ReadWriteFileIndexingStampImpl(42))
  }

  @Test
  fun `test NULL_STAMP is not equal to any other int, not even to NULL_INDEXING_STAMP`() {
    assertThat(ProjectIndexingDependenciesService.NULL_STAMP.isFileChanged(0)).isEqualTo(IsFileChangedResult.UNKNOWN)
    assertThat(ProjectIndexingDependenciesService.NULL_STAMP.isFileChanged(42)).isEqualTo(IsFileChangedResult.UNKNOWN)

    Assert.assertNotEquals(ProjectIndexingDependenciesService.NULL_STAMP, ReadWriteFileIndexingStampImpl(0))
    Assert.assertNotEquals(ProjectIndexingDependenciesService.NULL_STAMP, WriteOnlyFileIndexingStampImpl(0))
    Assert.assertNotEquals(ReadWriteFileIndexingStampImpl(0), ProjectIndexingDependenciesService.NULL_STAMP)
    Assert.assertNotEquals(WriteOnlyFileIndexingStampImpl(0), ProjectIndexingDependenciesService.NULL_STAMP)
    Assert.assertNotEquals(ProjectIndexingDependenciesService.NULL_STAMP, ReadWriteFileIndexingStampImpl(42))
    Assert.assertNotEquals(ReadWriteFileIndexingStampImpl(42), ProjectIndexingDependenciesService.NULL_STAMP)
    Assert.assertNotEquals(WriteOnlyFileIndexingStampImpl(42), ProjectIndexingDependenciesService.NULL_STAMP)
  }
}