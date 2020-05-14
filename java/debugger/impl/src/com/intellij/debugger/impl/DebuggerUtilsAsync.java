// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.impl;

import com.intellij.debugger.engine.SuspendContext;
import com.intellij.debugger.engine.SuspendContextImpl;
import com.intellij.debugger.engine.events.SuspendContextCommandImpl;
import com.intellij.openapi.util.registry.Registry;
import com.jetbrains.jdi.*;
import com.sun.jdi.*;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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

  public static CompletableFuture<List<Field>> allFields(ReferenceType type, SuspendContext context) {
    if (type instanceof ReferenceTypeImpl && Registry.is("debugger.async.jdi")) {
      return schedule((SuspendContextImpl)context, ((ReferenceTypeImpl)type).allFieldsAsync());
    }
    return CompletableFuture.completedFuture(type.allFields());
  }

  public static CompletableFuture<? extends Type> type(@Nullable Value value, SuspendContext context) {
    if (value == null) {
      return CompletableFuture.completedFuture(null);
    }
    if (value instanceof ObjectReferenceImpl && Registry.is("debugger.async.jdi")) {
      return schedule((SuspendContextImpl)context, ((ObjectReferenceImpl)value).typeAsync());
    }
    return CompletableFuture.completedFuture(value.type());
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
      return superclass((ClassType)type).thenCombine(interfaces((ClassType)type), (superclass, interfaces) ->
        StreamEx.<ReferenceType>ofNullable(superclass).prepend(interfaces));
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
