// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.testDiscovery.indices;

import com.intellij.concurrency.ConcurrentCollectionFactory;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.indexing.InvertedIndex;
import com.intellij.util.io.PersistentEnumerator;
import org.jetbrains.annotations.ApiStatus;

import java.io.IOException;
import java.util.Collection;

@ApiStatus.Internal
public final class PersistentObjectSeq {
  private static final Logger LOG = Logger.getInstance(PersistentObjectSeq.class);

  interface PersistentObject {
    void flush() throws Exception;

    void close() throws IOException;
  }

  private final Collection<PersistentObject> myObjects = ConcurrentCollectionFactory.createConcurrentSet();

  public void add(InvertedIndex<?, ?, ?> index) {
    myObjects.add(new PersistentObject() {
      @Override
      public void flush() throws Exception {
        index.flush();
      }

      @Override
      public void close() {
        index.dispose();
      }
    });
  }

  public void add(PersistentEnumerator<?> enumerator) {
    myObjects.add(new PersistentObject() {
      @Override
      public void flush() {
        if (enumerator.isDirty()) {
          enumerator.force();
        }
      }

      @Override
      public void close() throws IOException {
        enumerator.close();
      }
    });
  }

  public void close(boolean ignoreCloseProblem) {
    for (PersistentObject object : myObjects) {
      try {
        object.close();
      }
      catch (Throwable throwable) {
        if (!ignoreCloseProblem) throw new RuntimeException(throwable);
      }
    }
  }

  public void flush() {
    for (PersistentObject object : myObjects) {
      try {
        object.flush();
      }
      catch (Exception e) {
        LOG.error(e);
      }
    }
  }

  public void clear() {
    myObjects.clear();
  }
}
