// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.concurrency;

import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinWorkerThread;

public class PoisonFactory implements ForkJoinPool.ForkJoinWorkerThreadFactory{
  public PoisonFactory() {
    RuntimeException exception = new RuntimeException("ForkJoinPool initialized too early, IdeaForkJoinWorkerThreadFactory ignored");
    exception.printStackTrace();
    throw exception;
  }

  @Override
  public ForkJoinWorkerThread newThread(ForkJoinPool pool) {
    throw new RuntimeException();
  }
}
