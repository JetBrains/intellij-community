// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.impl;

import com.intellij.debugger.engine.DebuggerManagerThreadImpl;
import com.intellij.debugger.engine.DebuggerUtils;
import com.intellij.debugger.engine.SuspendContextImpl;
import com.intellij.debugger.engine.events.DebuggerCommandImpl;
import com.intellij.debugger.engine.events.SuspendContextCommandImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.psi.CommonClassNames;
import com.jetbrains.jdi.*;
import com.sun.jdi.*;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Function;
import java.util.stream.Stream;

import static java.util.concurrent.CompletableFuture.allOf;
import static java.util.concurrent.CompletableFuture.completedFuture;

public class DebuggerUtilsAsync {
  private static final Logger LOG = Logger.getInstance(DebuggerUtilsAsync.class);

  // Debugger manager thread
  public static CompletableFuture<String> getStringValue(StringReference value) {
    if (value instanceof StringReferenceImpl && Registry.is("debugger.async.jdi")) {
      return reschedule(((StringReferenceImpl)value).valueAsync());
    }
    return completedFuture(value.value());
  }

  public static CompletableFuture<List<Field>> allFields(ReferenceType type) {
    if (type instanceof ReferenceTypeImpl && Registry.is("debugger.async.jdi")) {
      return reschedule(((ReferenceTypeImpl)type).allFieldsAsync());
    }
    return completedFuture(type.allFields());
  }

  public static CompletableFuture<List<Field>> fields(ReferenceType type) {
    if (type instanceof ReferenceTypeImpl && Registry.is("debugger.async.jdi")) {
      return reschedule(((ReferenceTypeImpl)type).fieldsAsync());
    }
    return completedFuture(type.fields());
  }

  public static CompletableFuture<? extends Type> type(@Nullable Value value) {
    if (value == null) {
      return completedFuture(null);
    }
    if (value instanceof ObjectReferenceImpl && Registry.is("debugger.async.jdi")) {
      return reschedule(((ObjectReferenceImpl)value).typeAsync());
    }
    return completedFuture(value.type());
  }

  public static CompletableFuture<Value> getValue(ObjectReference ref, Field field) {
    if (ref instanceof ObjectReferenceImpl && Registry.is("debugger.async.jdi")) {
      return reschedule(((ObjectReferenceImpl)ref).getValueAsync(field));
    }
    return completedFuture(ref.getValue(field));
  }

  public static CompletableFuture<Map<Field, Value>> getValues(ObjectReference ref, List<Field> fields) {
    if (ref instanceof ObjectReferenceImpl && Registry.is("debugger.async.jdi")) {
      return reschedule(((ObjectReferenceImpl)ref).getValuesAsync(fields));
    }
    return completedFuture(ref.getValues(fields));
  }

  public static CompletableFuture<Map<Field, Value>> getValues(ReferenceType type, List<Field> fields) {
    if (type instanceof ReferenceTypeImpl && Registry.is("debugger.async.jdi")) {
      return reschedule(((ReferenceTypeImpl)type).getValuesAsync(fields));
    }
    return completedFuture(type.getValues(fields));
  }

  public static CompletableFuture<List<Value>> getValues(ArrayReference ref, int index, int length) {
    if (ref instanceof ArrayReferenceImpl && Registry.is("debugger.async.jdi")) {
      return reschedule(((ArrayReferenceImpl)ref).getValuesAsync(index, length));
    }
    return completedFuture(ref.getValues(index, length));
  }

  public static CompletableFuture<Integer> length(ArrayReference ref) {
    if (ref instanceof ArrayReferenceImpl && Registry.is("debugger.async.jdi")) {
      return reschedule(((ArrayReferenceImpl)ref).lengthAsync());
    }
    return completedFuture(ref.length());
  }

  public static CompletableFuture<Boolean> instanceOf(@Nullable Type subType, @NotNull String superType) {
    return instanceOf(subType, superType, true);
  }

  private static CompletableFuture<Boolean> instanceOf(@Nullable Type subType, @NotNull String superType, boolean reschedule) {
    if (!Registry.is("debugger.async.jdi")) {
      return completedFuture(DebuggerUtils.instanceOf(subType, superType));
    }

    if (subType == null || subType instanceof VoidType) {
      return completedFuture(false);
    }

    if (subType instanceof PrimitiveType) {
      return completedFuture(superType.equals(subType.name()));
    }

    if (CommonClassNames.JAVA_LANG_OBJECT.equals(superType)) {
      return completedFuture(true);
    }

    CompletableFuture<Boolean> res = new CompletableFuture<>();
    instanceOfObject(subType, superType, res).thenRun(() -> res.complete(false));
    if (!reschedule) {
      return res;
    }
    return reschedule(res);
  }

  private static CompletableFuture<Void> instanceOfObject(@Nullable Type subType,
                                                          @NotNull String superType,
                                                          CompletableFuture<Boolean> res) {
    if (subType == null || res.isDone()) {
      return completedFuture(null);
    }

    if (typeEquals(subType, superType)) {
      res.complete(true);
      return completedFuture(null); // early return
    }

    if (subType instanceof ClassType) {
      return allOf(
        superclass((ClassType)subType).thenCompose(s -> instanceOfObject(s, superType, res)),
        interfaces((ClassType)subType).thenCompose(
          interfaces -> allOf(interfaces.stream().map(i -> instanceOfObject(i, superType, res)).toArray(CompletableFuture[]::new))));
    }

    if (subType instanceof InterfaceType) {
      return allOf(
        superinterfaces((InterfaceType)subType).thenCompose(
          interfaces -> allOf(interfaces.stream().map(i -> instanceOfObject(i, superType, res)).toArray(CompletableFuture[]::new))));
    }

    if (subType instanceof ArrayType && superType.endsWith("[]")) {
      try {
        String superTypeItem = superType.substring(0, superType.length() - 2);
        Type subTypeItem = ((ArrayType)subType).componentType();
        return instanceOf(subTypeItem, superTypeItem, false).thenAccept(r -> {
          if (r) res.complete(true);
        });
      }
      catch (ClassNotLoadedException e) {
        //LOG.info(e);
      }
    }

    return completedFuture(null);
  }


  // Copied from DebuggerUtils
  private static boolean typeEquals(@NotNull Type type, @NotNull String typeName) {
    int genericPos = typeName.indexOf('<');
    if (genericPos > -1) {
      typeName = typeName.substring(0, genericPos);
    }
    return type.name().replace('$', '.').equals(typeName.replace('$', '.'));
  }

  public static CompletableFuture<Type> findAnyBaseType(@NotNull Type subType, Function<Type, CompletableFuture<Boolean>> checker) {
    CompletableFuture<Type> res = new CompletableFuture<>();
    findAnyBaseType(subType, checker, res).thenRun(() -> res.complete(null));
    return reschedule(res);
  }

  private static CompletableFuture<Void> findAnyBaseType(@Nullable Type type,
                                                         Function<Type, CompletableFuture<Boolean>> checker,
                                                         CompletableFuture<Type> res) {
    if (type == null || res.isDone()) {
      return completedFuture(null);
    }

    // check self
    CompletableFuture<Void> self = checker.apply(type).thenAccept(r -> {
      if (r) {
        res.complete(type);
      }
    });

    // check base types
    if (type instanceof ClassType) {
      return allOf(
        self,
        superclass((ClassType)type).thenCompose(s -> findAnyBaseType(s, checker, res)),
        interfaces((ClassType)type).thenCompose(
          interfaces -> allOf(interfaces.stream().map(i -> findAnyBaseType(i, checker, res)).toArray(CompletableFuture[]::new))));
    }

    if (type instanceof InterfaceType) {
      return allOf(
        self,
        superinterfaces((InterfaceType)type).thenCompose(
          interfaces -> allOf(interfaces.stream().map(i -> findAnyBaseType(i, checker, res)).toArray(CompletableFuture[]::new))));
    }

    return self;
  }

  // Reader thread
  public static CompletableFuture<List<Method>> methods(ReferenceType type) {
    if (type instanceof ReferenceTypeImpl && Registry.is("debugger.async.jdi")) {
      return ((ReferenceTypeImpl)type).methodsAsync();
    }
    return completedFuture(type.methods());
  }

  public static CompletableFuture<List<InterfaceType>> superinterfaces(InterfaceType iface) {
    if (iface instanceof InterfaceTypeImpl && Registry.is("debugger.async.jdi")) {
      return ((InterfaceTypeImpl)iface).superinterfacesAsync();
    }
    return completedFuture(iface.superinterfaces());
  }

  public static CompletableFuture<ClassType> superclass(ClassType cls) {
    if (cls instanceof ClassTypeImpl && Registry.is("debugger.async.jdi")) {
      return ((ClassTypeImpl)cls).superclassAsync();
    }
    return completedFuture(cls.superclass());
  }

  public static CompletableFuture<List<InterfaceType>> interfaces(ClassType cls) {
    if (cls instanceof ClassTypeImpl && Registry.is("debugger.async.jdi")) {
      return ((ClassTypeImpl)cls).interfacesAsync();
    }
    return completedFuture(cls.interfaces());
  }

  public static CompletableFuture<Stream<? extends ReferenceType>> supertypes(ReferenceType type) {
    if (!Registry.is("debugger.async.jdi")) {
      return completedFuture(DebuggerUtilsImpl.supertypes(type));
    }
    if (type instanceof InterfaceType) {
      return superinterfaces(((InterfaceType)type)).thenApply(Collection::stream);
    }
    else if (type instanceof ClassType) {
      return superclass((ClassType)type).thenCombine(interfaces((ClassType)type), (superclass, interfaces) ->
        StreamEx.<ReferenceType>ofNullable(superclass).prepend(interfaces));
    }
    return completedFuture(StreamEx.empty());
  }

  /**
   * Schedule future completion in a separate command with the same priority and suspend context (if available)
   * as in the command being processed at the moment
   */
  public static <T> CompletableFuture<T> reschedule(CompletableFuture<T> future) {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    DebuggerManagerThreadImpl thread = (DebuggerManagerThreadImpl)InvokeThread.currentThread();
    LOG.assertTrue(thread != null);
    DebuggerCommandImpl event = DebuggerManagerThreadImpl.getCurrentCommand();
    LOG.assertTrue(event != null);
    PrioritizedTask.Priority priority = event.getPriority();
    SuspendContextImpl suspendContext =
      event instanceof SuspendContextCommandImpl ? ((SuspendContextCommandImpl)event).getSuspendContext() : null;

    CompletableFuture<T> res = new CompletableFuture<>();
    future.whenComplete((r, ex) -> {
      if (DebuggerManagerThreadImpl.isManagerThread()) {
        completeFuture(r, ex, res);
      }
      else if (suspendContext != null) {
        thread.schedule(new SuspendContextCommandImpl(suspendContext) {
          @Override
          public Priority getPriority() {
            return priority;
          }

          @Override
          public void contextAction(@NotNull SuspendContextImpl suspendContext) {
            completeFuture(r, ex, res);
          }

          @Override
          protected void commandCancelled() {
            res.cancel(false);
          }
        });
      }
      else {
        thread.schedule(new DebuggerCommandImpl(priority) {
          @Override
          protected void action() {
            completeFuture(r, ex, res);
          }

          @Override
          protected void commandCancelled() {
            res.cancel(false);
          }
        });
      }
    });
    return res;
  }

  public static Throwable unwrap(Throwable throwable) {
    return throwable instanceof CompletionException ? throwable.getCause() : throwable;
  }

  private static <T> void completeFuture(T res, Throwable ex, CompletableFuture<T> future) {
    if (ex != null) {
      future.completeExceptionally(ex);
    }
    else {
      future.complete(res);
    }
  }
}
