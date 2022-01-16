// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.impl.compilation

import groovy.transform.CompileStatic
import groovy.transform.PackageScope
import org.jetbrains.annotations.NotNull
import org.jetbrains.intellij.build.BuildMessages

import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicInteger

@CompileStatic
@PackageScope
final class NamedThreadPoolExecutor extends ThreadPoolExecutor {
  private final AtomicInteger counter = new AtomicInteger()
  private final List<Future> futures = new LinkedList<Future>()
  private final ConcurrentLinkedDeque<Throwable> errors = new ConcurrentLinkedDeque<Throwable>()

  NamedThreadPoolExecutor(String threadNamePrefix, int maximumPoolSize) {
    super(maximumPoolSize, maximumPoolSize, 1, TimeUnit.MINUTES, new LinkedBlockingDeque(4096))
    setThreadFactory(new ThreadFactory() {
      @NotNull
      @Override
      Thread newThread(@NotNull Runnable r) {
        Thread thread = new Thread(r, threadNamePrefix + ' ' + counter.incrementAndGet())
        thread.setPriority(Thread.NORM_PRIORITY - 1)
        return thread
      }
    })
  }

  void close() {
    shutdown()
    awaitTermination(10, TimeUnit.SECONDS)
    shutdownNow()
  }

  void submit(Closure<?> block) {
    futures.add(this.submit(new Runnable() {
      @Override
      void run() {
        try {
          block()
        }
        catch (Throwable e) {
          errors.add(e)
        }
      }
    }))
  }

  boolean reportErrors(BuildMessages messages) {
    def size = errors.size()
    if (size != 0) {
      def prefix = size == 1 ? "Error occurred" : "$size errors occurred"
      messages.warning(prefix + ":")
      errors.each { Throwable t ->
        def writer = new StringWriter()
        new PrintWriter(writer).withCloseable { t?.printStackTrace(it) }
        messages.warning("${t.message}\n$writer")
      }
      messages.error(prefix + ", see details above")
      return true
    }
    return false
  }

  void waitForAllComplete(BuildMessages messages) {
    while (!futures.isEmpty()) {
      def iterator = futures.listIterator()
      while (iterator.hasNext()) {
        Future f = iterator.next()
        if (f.isDone()) {
          iterator.remove()
        }
      }
      def size = futures.size()
      if (size == 0) break
      messages.info("$size task${size != 1 ? 's' : ''} left...")
      if (size < 100) {
        futures.last().get()
      }
      else {
        Thread.sleep(TimeUnit.SECONDS.toMillis(1))
      }
    }
  }
}
