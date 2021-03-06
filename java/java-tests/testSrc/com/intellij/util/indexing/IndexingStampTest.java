// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileWithId;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.jetbrains.jetCheck.Generator;
import org.jetbrains.jetCheck.PropertyChecker;

import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

public class IndexingStampTest extends LightJavaCodeInsightFixtureTestCase {

  public void testIndexingStampSerialization() {
    VirtualFile file = myFixture.getTempDirFixture().createFile("tempfile.txt");
    int fileId = ((VirtualFileWithId)file).getId();

    Generator<ID<?, ?>> indexIds =
      Generator.sampledFrom(FileBasedIndexExtension
                              .EXTENSION_POINT_NAME
                              .extensions()
                              .map(ex -> ex.getName())
                              .sorted(Comparator.comparing(id -> id.getName()))
                              .collect(Collectors.toList()));

    Generator<ChangeIndexedStampForIndexOp> changeIndexedStampOps =
      Generator.sampledFrom((id, indexId) -> IndexingStamp.setFileIndexedStateCurrent(id, indexId),
                            (id, indexId) -> IndexingStamp.setFileIndexedStateOutdated(id, indexId),
                            (id, indexId) -> IndexingStamp.setFileIndexedStateUnindexed(id, indexId));

    Generator<ChangeIndexedStamp> stampGenerator =
      Generator.zipWith(indexIds, changeIndexedStampOps, (id, op) -> new ChangeIndexedStamp(op, id));

    PropertyChecker.customized().withIterationCount(30).forAll(stampGenerator, stamp -> {

      stamp.setIndexedStamp(fileId);
      Set<ID<?, ?>> nonTrivialIndexesBeforeFlush = new HashSet<>(IndexingStamp.getNontrivialFileIndexedStates(fileId));

      IndexingStamp.flushCache(fileId);
      IndexingStamp.flushCaches();

      Set<ID<?, ?>> nonTrivialIndexesAfterFlush = new HashSet<>(IndexingStamp.getNontrivialFileIndexedStates(fileId));
      boolean equals = nonTrivialIndexesBeforeFlush.equals(nonTrivialIndexesAfterFlush);
      IndexingStamp.flushCaches();
      return equals;
    });
  }

  @FunctionalInterface
  private interface ChangeIndexedStampForIndexOp {
    void setIndexedStamp(int fileId, ID<?, ?> indexId);
  }

  private static class ChangeIndexedStamp {
    private final ChangeIndexedStampForIndexOp myOp;
    private final ID<?, ?> myIndexId;

    private ChangeIndexedStamp(ChangeIndexedStampForIndexOp op, ID<?, ?> id) {
      myOp = op;
      myIndexId = id;
    }

    void setIndexedStamp(int fileId) {
      myOp.setIndexedStamp(fileId, myIndexId);
    }
  }

}
