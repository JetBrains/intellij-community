// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.impl;

import com.intellij.debugger.engine.SuspendContext;
import com.intellij.debugger.engine.SuspendContextImpl;
import com.intellij.debugger.engine.events.SuspendContextCommandImpl;
import com.intellij.openapi.util.registry.Registry;
import com.jetbrains.jdi.StringReferenceImpl;
import com.sun.jdi.StringReference;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;

public class DebuggerUtilsAsync {
  public static CompletableFuture<String> getStringValue(StringReference value, SuspendContext context) {
    if (value instanceof StringReferenceImpl && Registry.is("debugger.async.jdi")) {
      return schedule((SuspendContextImpl)context, ((StringReferenceImpl)value).valueAsync());
    }
    return CompletableFuture.completedFuture(value.value());
  }

  private static <T> CompletableFuture<T> schedule(SuspendContextImpl context, CompletableFuture<T> future) {
    if (future.isDone()) {
      return future;
    }

    CompletableFuture<T> res = new CompletableFuture<>();
    future.whenComplete((r, ex) -> {
      context.getDebugProcess().getManagerThread().schedule(new SuspendContextCommandImpl(context) {
        @Override
        public void contextAction(@NotNull SuspendContextImpl suspendContext) {
          if (ex != null) {
            res.completeExceptionally(ex);
          }
          else {
            res.complete(r);
          }
        }
      });
    });
    return res;
  }
}
