// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.impl;

import com.intellij.debugger.engine.SuspendContext;
import com.intellij.debugger.engine.SuspendContextImpl;
import com.intellij.debugger.engine.events.SuspendContextCommandImpl;
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

  public static CompletableFuture<Value> getValue(ObjectReference ref, Field field, @Nullable SuspendContext context) {
    if (ref instanceof ObjectReferenceImpl && Registry.is("debugger.async.jdi") && context != null) {
      return schedule((SuspendContextImpl)context, ((ObjectReferenceImpl)ref).getValueAsync(field));
    }
    return CompletableFuture.completedFuture(ref.getValue(field));
  }

  public static CompletableFuture<Map<Field, Value>> getValues(ObjectReference ref, List<Field> fields, @Nullable SuspendContext context) {
    if (ref instanceof ObjectReferenceImpl && Registry.is("debugger.async.jdi") && context != null) {
      return schedule((SuspendContextImpl)context, ((ObjectReferenceImpl)ref).getValuesAsync(fields));
    }
    return CompletableFuture.completedFuture(ref.getValues(fields));
  }

  public static CompletableFuture<Map<Field, Value>> getValues(ReferenceType type, List<Field> fields, @Nullable SuspendContext context) {
    if (type instanceof ReferenceTypeImpl && Registry.is("debugger.async.jdi") && context != null) {
      return schedule((SuspendContextImpl)context, ((ReferenceTypeImpl)type).getValuesAsync(fields));
    }
    return CompletableFuture.completedFuture(type.getValues(fields));
  }

  public static CompletableFuture<Integer> length(ArrayReference ref, @Nullable SuspendContext context) {
    if (ref instanceof ArrayReferenceImpl && Registry.is("debugger.async.jdi") && context != null) {
      return schedule((SuspendContextImpl)context, ((ArrayReferenceImpl)ref).lengthAsync());
    }
    return CompletableFuture.completedFuture(ref.length());
  }

  public static CompletableFuture<Boolean> instanceOf(@Nullable Type subType, @NotNull String superType, @Nullable SuspendContext context) {
    if (subType == null || subType instanceof VoidType) {
      return CompletableFuture.completedFuture(false);
    }

    if (subType instanceof PrimitiveType) {
      return CompletableFuture.completedFuture(superType.equals(subType.name()));
    }

    if (CommonClassNames.JAVA_LANG_OBJECT.equals(superType)) {
      return CompletableFuture.completedFuture(true);
    }

    CompletableFuture<Boolean> res = new CompletableFuture<>();
    instanceOfObject(subType, superType, res).thenRun(() -> res.complete(false));
    return schedule((SuspendContextImpl)context, res);
  }

  private static CompletableFuture<Void> instanceOfObject(@Nullable Type subType,
                                                          @NotNull String superType,
                                                          CompletableFuture<Boolean> res) {
    if (subType == null) {
      return CompletableFuture.completedFuture(null);
    }

    if (typeEquals(subType, superType)) {
      res.complete(true);
    }

    if (subType instanceof ClassType) {
      return CompletableFuture.allOf(
        superclass((ClassType)subType).thenCompose(s -> instanceOfObject(s, superType, res)),
        interfaces((ClassType)subType).thenCompose(interfaces -> CompletableFuture
          .allOf(interfaces.stream().map(i -> instanceOfObject(i, superType, res)).toArray(CompletableFuture[]::new))));
    }

    if (subType instanceof InterfaceType) {
      return CompletableFuture.allOf(
        superinterfaces((InterfaceType)subType).thenCompose(interfaces -> CompletableFuture
          .allOf(interfaces.stream().map(i -> instanceOfObject(i, superType, res)).toArray(CompletableFuture[]::new))));
    }

    if (subType instanceof ArrayType && superType.endsWith("[]")) {
      try {
        String superTypeItem = superType.substring(0, superType.length() - 2);
        Type subTypeItem = ((ArrayType)subType).componentType();
        return instanceOf(subTypeItem, superTypeItem, null).thenAccept(r -> {
          if (r) res.complete(true);
        });
      }
      catch (ClassNotLoadedException e) {
        //LOG.info(e);
      }
    }

    return CompletableFuture.completedFuture(null);
  }


  // Copied from DebuggerUtils
  private static boolean typeEquals(@NotNull Type type, @NotNull String typeName) {
    int genericPos = typeName.indexOf('<');
    if (genericPos > -1) {
      typeName = typeName.substring(0, genericPos);
    }
    return type.name().replace('$', '.').equals(typeName.replace('$', '.'));
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

  private static <T> CompletableFuture<T> schedule(@Nullable SuspendContextImpl context, CompletableFuture<T> future) {
    if (future.isDone() || context == null) {
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
