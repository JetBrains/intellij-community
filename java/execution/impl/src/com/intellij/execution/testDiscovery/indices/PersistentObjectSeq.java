// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.testDiscovery.indices;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.indexing.InvertedIndex;
import com.intellij.util.io.PersistentEnumeratorDelegate;

import java.io.IOException;
import java.util.Collection;

class PersistentObjectSeq {
  private static final Logger LOG = Logger.getInstance(PersistentObjectSeq.class);

  interface PersistentObject {
    void flush() throws Exception;

    void close() throws IOException;
  }

  private final Collection<PersistentObject> myObjects = ContainerUtil.newConcurrentSet();

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

  public void add(PersistentEnumeratorDelegate<?> enumerator) {
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
