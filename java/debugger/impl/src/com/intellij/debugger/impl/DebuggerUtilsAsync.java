// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.impl;

import com.intellij.debugger.engine.SuspendContext;
import com.intellij.debugger.engine.SuspendContextImpl;
import com.intellij.debugger.engine.events.SuspendContextCommandImpl;
import com.intellij.openapi.util.registry.Registry;
import com.jetbrains.jdi.ClassTypeImpl;
import com.jetbrains.jdi.InterfaceTypeImpl;
import com.jetbrains.jdi.StringReferenceImpl;
import com.sun.jdi.ClassType;
import com.sun.jdi.InterfaceType;
import com.sun.jdi.ReferenceType;
import com.sun.jdi.StringReference;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

public class DebuggerUtilsAsync {
  // Debugger manager thread
  public static CompletableFuture<String> getStringValue(StringReference value, SuspendContext context) {
    if (value instanceof StringReferenceImpl && Registry.is("debugger.async.jdi")) {
      return schedule((SuspendContextImpl)context, ((StringReferenceImpl)value).valueAsync());
    }
    return CompletableFuture.completedFuture(value.value());
  }

  // Reader thread
  public static CompletableFuture<List<InterfaceType>> superinterfaces(InterfaceType iface) {
    if (iface instanceof InterfaceTypeImpl && Registry.is("debugger.async.jdi")) {
      return ((InterfaceTypeImpl)iface).superinterfacesAsync();
    }
    return CompletableFuture.completedFuture(iface.superinterfaces());
  }

  public static CompletableFuture<ClassType> superclass(ClassType cls) {
    if (cls instanceof ClassTypeImpl && Registry.is("debugger.async.jdi")) {
      return ((ClassTypeImpl)cls).superclassAsync();
    }
    return CompletableFuture.completedFuture(cls.superclass());
  }

  public static CompletableFuture<List<InterfaceType>> interfaces(ClassType cls) {
    if (cls instanceof ClassTypeImpl && Registry.is("debugger.async.jdi")) {
      return ((ClassTypeImpl)cls).interfacesAsync();
    }
    return CompletableFuture.completedFuture(cls.interfaces());
  }

  public static CompletableFuture<Stream<? extends ReferenceType>> supertypes(ReferenceType type) {
    if (!Registry.is("debugger.async.jdi")) {
      return CompletableFuture.completedFuture(DebuggerUtilsImpl.supertypes(type));
    }
    if (type instanceof InterfaceType) {
      return superinterfaces(((InterfaceType)type)).thenApply(Collection::stream);
    }
    else if (type instanceof ClassType) {
      CompletableFuture<ClassType> superclass = superclass((ClassType)type);
      CompletableFuture<List<InterfaceType>> interfaces = interfaces((ClassType)type);
      return CompletableFuture.allOf(superclass, interfaces)
        .thenApply(r -> StreamEx.<ReferenceType>ofNullable(superclass.join()).prepend(interfaces.join()));
    }
    return CompletableFuture.completedFuture(StreamEx.empty());
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
