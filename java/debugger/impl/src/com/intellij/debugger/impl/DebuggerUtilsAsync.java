// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.impl;

import com.intellij.debugger.engine.DebuggerManagerThreadImpl;
import com.intellij.debugger.engine.DebuggerUtils;
import com.intellij.debugger.engine.SuspendContextImpl;
import com.intellij.debugger.engine.events.DebuggerCommandImpl;
import com.intellij.debugger.engine.events.SuspendContextCommandImpl;
import com.intellij.diagnostic.ThreadDumper;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.psi.CommonClassNames;
import com.intellij.util.ThrowableRunnable;
import com.jetbrains.jdi.*;
import com.sun.jdi.*;
import com.sun.jdi.event.EventSet;
import com.sun.jdi.request.EventRequest;
import com.sun.jdi.request.EventRequestManager;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;
import java.util.stream.Stream;

import static java.util.concurrent.CompletableFuture.allOf;
import static java.util.concurrent.CompletableFuture.completedFuture;

public final class DebuggerUtilsAsync {
  private static final Logger LOG = Logger.getInstance(DebuggerUtilsAsync.class);

  public static boolean isAsyncEnabled() {
    return Registry.is("debugger.async.jdi");
  }

  // Debugger manager thread
  public static CompletableFuture<String> getStringValue(StringReference value) {
    if (value instanceof StringReferenceImpl && isAsyncEnabled()) {
      return reschedule(((StringReferenceImpl)value).valueAsync());
    }
    return completedFuture(value.value());
  }

  public static CompletableFuture<List<Method>> allMethods(ReferenceType type) {
    if (type instanceof ReferenceTypeImpl && isAsyncEnabled()) {
      return reschedule(((ReferenceTypeImpl)type).allMethodsAsync());
    }
    return completedFuture(type.allMethods());
  }

  public static CompletableFuture<List<Field>> allFields(ReferenceType type) {
    if (type instanceof ReferenceTypeImpl && isAsyncEnabled()) {
      return reschedule(((ReferenceTypeImpl)type).allFieldsAsync());
    }
    return completedFuture(type.allFields());
  }

  public static CompletableFuture<List<Field>> fields(ReferenceType type) {
    if (type instanceof ReferenceTypeImpl && isAsyncEnabled()) {
      return reschedule(((ReferenceTypeImpl)type).fieldsAsync());
    }
    return completedFuture(type.fields());
  }

  public static CompletableFuture<? extends Type> type(@Nullable Value value) {
    if (value == null) {
      return completedFuture(null);
    }
    if (value instanceof ObjectReferenceImpl && isAsyncEnabled()) {
      return reschedule(((ObjectReferenceImpl)value).typeAsync());
    }
    return completedFuture(value.type());
  }

  public static CompletableFuture<Value> getValue(ObjectReference ref, Field field) {
    if (ref instanceof ObjectReferenceImpl && isAsyncEnabled()) {
      return reschedule(((ObjectReferenceImpl)ref).getValueAsync(field));
    }
    return completedFuture(ref.getValue(field));
  }

  public static CompletableFuture<Map<Field, Value>> getValues(ObjectReference ref, List<Field> fields) {
    if (ref instanceof ObjectReferenceImpl && isAsyncEnabled()) {
      return reschedule(((ObjectReferenceImpl)ref).getValuesAsync(fields));
    }
    return completedFuture(ref.getValues(fields));
  }

  public static CompletableFuture<Map<Field, Value>> getValues(ReferenceType type, List<Field> fields) {
    if (type instanceof ReferenceTypeImpl && isAsyncEnabled()) {
      return reschedule(((ReferenceTypeImpl)type).getValuesAsync(fields));
    }
    return completedFuture(type.getValues(fields));
  }

  public static CompletableFuture<List<Value>> getValues(ArrayReference ref, int index, int length) {
    if (ref instanceof ArrayReferenceImpl && isAsyncEnabled()) {
      return reschedule(((ArrayReferenceImpl)ref).getValuesAsync(index, length));
    }
    return completedFuture(ref.getValues(index, length));
  }

  public static CompletableFuture<List<ThreadReference>> allThreads(VirtualMachine vm) {
    if (vm instanceof VirtualMachineImpl && isAsyncEnabled()) {
      return reschedule(((VirtualMachineImpl)vm).allThreadsAsync());
    }
    return completedFuture(vm.allThreads());
  }


  public static CompletableFuture<Integer> length(ArrayReference ref) {
    if (ref instanceof ArrayReferenceImpl && isAsyncEnabled()) {
      return reschedule(((ArrayReferenceImpl)ref).lengthAsync());
    }
    return completedFuture(ref.length());
  }

  public static CompletableFuture<String> sourceName(ReferenceType type) {
    if (type instanceof ReferenceTypeImpl && isAsyncEnabled()) {
      return reschedule(((ReferenceTypeImpl)type).sourceNameAsync());
    }
    return toCompletableFuture(() -> type.sourceName());
  }

  public static CompletableFuture<List<String>> availableStrata(ReferenceType type) {
    if (type instanceof ReferenceTypeImpl && isAsyncEnabled()) {
      return reschedule(((ReferenceTypeImpl)type).availableStrataAsync());
    }
    return toCompletableFuture(() -> type.availableStrata());
  }

  public static CompletableFuture<List<Location>> locationsOfLine(@NotNull ReferenceType type, int lineNumber) {
    return locationsOfLine(type, type.virtualMachine().getDefaultStratum(), null, lineNumber);
  }

  /**
   * Drop-in replacement for the standard jdi version, but "parallel" inside, so a lot faster when type has lots of methods
   */
  public static List<Location> locationsOfLineSync(@NotNull ReferenceType type, int lineNumber)
    throws AbsentInformationException {
    return locationsOfLineSync(type, type.virtualMachine().getDefaultStratum(), null, lineNumber);
  }

  public static CompletableFuture<List<Location>> locationsOfLine(ReferenceType type, String stratum, String sourceName, int lineNumber) {
    if (type instanceof ReferenceTypeImpl && isAsyncEnabled()) {
      return reschedule(((ReferenceTypeImpl)type).locationsOfLineAsync(stratum, sourceName, lineNumber));
    }
    return toCompletableFuture(() -> type.locationsOfLine(stratum, sourceName, lineNumber));
  }

  /**
   * Drop-in replacement for the standard jdi version, but "parallel" inside, so a lot faster when type has lots of methods
   */
  public static List<Location> locationsOfLineSync(ReferenceType type, String stratum, String sourceName, int lineNumber)
    throws AbsentInformationException {
    if (type instanceof ReferenceTypeImpl && isAsyncEnabled()) {
      try {
        return ((ReferenceTypeImpl)type).locationsOfLineAsync(stratum, sourceName, lineNumber).get();
      }
      catch (Exception e) {
        if (e.getCause() instanceof AbsentInformationException) {
          throw (AbsentInformationException)e.getCause();
        }
        LOG.warn(e);
      }
    }
    return type.locationsOfLine(stratum, sourceName, lineNumber);
  }

  public static List<Location> allLineLocationsSync(ReferenceType type) throws AbsentInformationException {
    return allLineLocationsSync(type, type.virtualMachine().getDefaultStratum(), null);
  }

  /**
   * Drop-in replacement for the standard jdi version, but "parallel" inside, so a lot faster when type has lots of methods
   */
  public static List<Location> allLineLocationsSync(ReferenceType type, String stratum, String sourceName)
    throws AbsentInformationException {
    if (type instanceof ReferenceTypeImpl && isAsyncEnabled()) {
      try {
        return ((ReferenceTypeImpl)type).allLineLocationsAsync(stratum, sourceName).get();
      }
      catch (Exception e) {
        if (e.getCause() instanceof AbsentInformationException) {
          throw (AbsentInformationException)e.getCause();
        }
        LOG.warn(e);
      }
    }
    return type.allLineLocations(stratum, sourceName);
  }

  public static CompletableFuture<List<Location>> allLineLocations(Method method) {
    if (method instanceof MethodImpl && isAsyncEnabled()) {
      return reschedule(((MethodImpl)method).allLineLocationsAsync());
    }
    return toCompletableFuture(() -> method.allLineLocations());
  }

  public static CompletableFuture<Boolean> instanceOf(@Nullable Type subType, @NotNull String superType) {
    return instanceOf(subType, superType, true);
  }

  private static CompletableFuture<Boolean> instanceOf(@Nullable Type subType, @NotNull String superType, boolean reschedule) {
    if (!isAsyncEnabled()) {
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

  public static CompletableFuture<Void> deleteEventRequest(EventRequestManager eventRequestManager, EventRequest request) {
    if (isAsyncEnabled() && eventRequestManager instanceof EventRequestManagerImpl) {
      return ((EventRequestManagerImpl)eventRequestManager).deleteEventRequestAsync(request);
    }
    else {
      try {
        eventRequestManager.deleteEventRequest(request);
      }
      catch (ArrayIndexOutOfBoundsException e) {
        LOG.error("Exception in EventRequestManager.deleteEventRequest", e, ThreadDumper.dumpThreadsToString());
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

  public static CompletableFuture<Type> findAnyBaseType(@NotNull Type subType,
                                                        Function<? super Type, ? extends CompletableFuture<Boolean>> checker) {
    CompletableFuture<Type> res = new CompletableFuture<>();
    findAnyBaseType(subType, checker, res).thenRun(() -> res.complete(null));
    return reschedule(res);
  }

  private static CompletableFuture<Void> findAnyBaseType(@Nullable Type type,
                                                         Function<? super Type, ? extends CompletableFuture<Boolean>> checker,
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

  public static CompletableFuture<Method> method(Location location) {
    if (location instanceof LocationImpl && isAsyncEnabled()) {
      return reschedule(DebuggerUtilsEx.getMethodAsync((LocationImpl)location));
    }
    return toCompletableFuture(() -> DebuggerUtilsEx.getMethod(location));
  }

  public static CompletableFuture<Boolean> isObsolete(Method method) {
    if (method instanceof MethodImpl && isAsyncEnabled()) {
      return reschedule(((MethodImpl)method).isObsoleteAsync());
    }
    return toCompletableFuture(() -> method.isObsolete());
  }

  public static CompletableFuture<StackFrame> frame(ThreadReference thread, int index) {
    if (thread instanceof ThreadReferenceImpl && isAsyncEnabled()) {
      return reschedule(((ThreadReferenceImpl)thread).frameAsync(index));
    }
    return toCompletableFuture(() -> thread.frame(index));
  }

  public static CompletableFuture<List<StackFrame>> frames(ThreadReference thread, int start, int length) {
    if (thread instanceof ThreadReferenceImpl && isAsyncEnabled()) {
      return reschedule(((ThreadReferenceImpl)thread).framesAsync(start, length));
    }
    return toCompletableFuture(() -> thread.frames(start, length));
  }

  public static CompletableFuture<Integer> frameCount(ThreadReference thread) {
    if (thread instanceof ThreadReferenceImpl && isAsyncEnabled()) {
      return reschedule(((ThreadReferenceImpl)thread).frameCountAsync());
    }
    return toCompletableFuture(() -> thread.frameCount());
  }


  // Reader thread
  public static CompletableFuture<List<Method>> methods(ReferenceType type) {
    if (type instanceof ReferenceTypeImpl && isAsyncEnabled()) {
      return ((ReferenceTypeImpl)type).methodsAsync();
    }
    return completedFuture(type.methods());
  }

  public static CompletableFuture<List<InterfaceType>> superinterfaces(InterfaceType iface) {
    if (iface instanceof InterfaceTypeImpl && isAsyncEnabled()) {
      return ((InterfaceTypeImpl)iface).superinterfacesAsync();
    }
    return completedFuture(iface.superinterfaces());
  }

  public static CompletableFuture<ClassType> superclass(ClassType cls) {
    if (cls instanceof ClassTypeImpl && isAsyncEnabled()) {
      return ((ClassTypeImpl)cls).superclassAsync();
    }
    return completedFuture(cls.superclass());
  }

  public static CompletableFuture<List<InterfaceType>> interfaces(ClassType cls) {
    if (cls instanceof ClassTypeImpl && isAsyncEnabled()) {
      return ((ClassTypeImpl)cls).interfacesAsync();
    }
    return completedFuture(cls.interfaces());
  }

  public static CompletableFuture<Stream<? extends ReferenceType>> supertypes(ReferenceType type) {
    if (!isAsyncEnabled()) {
      return toCompletableFuture(() -> DebuggerUtilsImpl.supertypes(type));
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

  public static CompletableFuture<byte[]> bytecodes(Method method) {
    if (method instanceof MethodImpl && isAsyncEnabled()) {
      return ((MethodImpl)method).bytecodesAsync();
    }
    return toCompletableFuture(() -> method.bytecodes());
  }

  public static CompletableFuture<byte[]> constantPool(ReferenceType type) {
    if (type instanceof ReferenceTypeImpl && isAsyncEnabled()) {
      return ((ReferenceTypeImpl)type).constantPoolAsync();
    }
    return toCompletableFuture(() -> type.constantPool());
  }

  public static CompletableFuture<Void> setEnabled(EventRequest request, boolean value) {
    EventRequestManager eventRequestManager = request.virtualMachine().eventRequestManager();
    if (eventRequestManager instanceof EventRequestManagerImpl && isAsyncEnabled()) {
      return ((EventRequestManagerImpl)eventRequestManager).setEnabledAsync(request, value);
    }
    return toCompletableFuture(() -> request.setEnabled(value));
  }

  public static CompletableFuture<Void> resume(VirtualMachine vm) {
    if (vm instanceof VirtualMachineImpl && isAsyncEnabled()) {
      return ((VirtualMachineImpl)vm).resumeAsync();
    }
    return toCompletableFuture(() -> vm.resume());
  }

  public static CompletableFuture<Void> resume(ThreadReference thread) {
    LOG.assertTrue(thread.suspendCount() > 0, "Suspend count must be greater zero before resume, " + thread);
    if (thread instanceof ThreadReferenceImpl && isAsyncEnabled()) {
      return ((ThreadReferenceImpl)thread).resumeAsync();
    }
    return toCompletableFuture(() -> thread.resume());
  }

  public static CompletableFuture<Void> resume(EventSet eventSet) {
    if (eventSet instanceof EventSetImpl && isAsyncEnabled()) {
      return ((EventSetImpl)eventSet).resumeAsync();
    }
    return toCompletableFuture(() -> eventSet.resume());
  }

  public static CompletableFuture<List<ReferenceType>> allCLasses(VirtualMachine virtualMachine) {
    if (virtualMachine instanceof VirtualMachineImpl && isAsyncEnabled()) {
      return ((VirtualMachineImpl)virtualMachine).allClassesAsync();
    }
    return toCompletableFuture(() -> virtualMachine.allClasses());
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

    CompletableFuture<T> res = new DebuggerCompletableFuture<>();
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

  public static Throwable unwrap(@Nullable Throwable throwable) {
    return throwable instanceof CompletionException || throwable instanceof ExecutionException ? throwable.getCause() : throwable;
  }

  public static <T> T logError(@NotNull Throwable throwable) {
    Throwable e = unwrap(throwable);
    if (!(e instanceof CancellationException)) {
      DebuggerUtilsImpl.logError(e.getMessage(), e, true); // wrap to keep the exact catch position
    }
    return null;
  }

  public static <T, E extends Exception> CompletableFuture<T> toCompletableFuture(ThrowableComputable<? extends T, E> provider) {
    try {
      return completedFuture(provider.compute());
    }
    catch (Exception e) {
      return CompletableFuture.failedFuture(e);
    }
  }

  public static <E extends Exception> CompletableFuture<Void> toCompletableFuture(ThrowableRunnable<E> provider) {
    try {
      provider.run();
      return completedFuture(null);
    }
    catch (Exception e) {
      return CompletableFuture.failedFuture(e);
    }
  }

  public static <T> void completeFuture(T res, Throwable ex, CompletableFuture<T> future) {
    if (ex != null) {
      future.completeExceptionally(ex);
    }
    else {
      future.complete(res);
    }
  }
}
