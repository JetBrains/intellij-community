// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.incremental;

import com.intellij.util.SmartList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.builders.java.dependencyView.Callbacks;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * @author Eugene Zhuravlev
 * Date: 21-Feb-18
 */
class CompositeConstantResolver implements Callbacks.ConstantAffectionResolver {
  private final Collection<Callbacks.ConstantAffectionResolver> myResolvers;

  public CompositeConstantResolver(@NotNull Collection<Callbacks.ConstantAffectionResolver> resolvers) {
    myResolvers = resolvers;
  }

  @Override
  public Future<Callbacks.ConstantAffection> request(String ownerClassName, String fieldName, int accessFlags, boolean fieldRemoved, boolean accessChanged) {
    final List<Future<Callbacks.ConstantAffection>> futures = new SmartList<>();
    for (Callbacks.ConstantAffectionResolver resolver : myResolvers) {
      futures.add(resolver.request(ownerClassName, fieldName, accessFlags, fieldRemoved, accessChanged));
    }
    return new Future<Callbacks.ConstantAffection>() {
      @Override
      public boolean cancel(boolean mayInterruptIfRunning) {
        boolean res = !futures.isEmpty();
        for (Future<Callbacks.ConstantAffection> future : futures) {
          res &= future.cancel(mayInterruptIfRunning);
        }
        return res;
      }

      @Override
      public boolean isCancelled() {
        for (Future<Callbacks.ConstantAffection> future : futures) {
          if (!future.isCancelled()) {
            return false;
          }
        }
        return !futures.isEmpty();
      }

      @Override
      public boolean isDone() {
        for (Future<Callbacks.ConstantAffection> future : futures) {
          if (!future.isDone()) {
            return false;
          }
        }
        return true;
      }

      @Override
      public Callbacks.ConstantAffection get() throws InterruptedException, ExecutionException {
        final List<Callbacks.ConstantAffection> results = new SmartList<>();
        for (Future<Callbacks.ConstantAffection> future : futures) {
          results.add(future.get());
        }
        return Callbacks.ConstantAffection.compose(results);
      }

      @Override
      public Callbacks.ConstantAffection get(long timeout, @NotNull TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        final List<Callbacks.ConstantAffection> results = new SmartList<>();
        for (Future<Callbacks.ConstantAffection> future : futures) {
          results.add(future.get(timeout, unit));
        }
        return Callbacks.ConstantAffection.compose(results);
      }
    };
  }
}
