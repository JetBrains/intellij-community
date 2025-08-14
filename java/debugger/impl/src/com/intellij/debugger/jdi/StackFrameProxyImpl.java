// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

/*
 * @author Eugene Zhuravlev
 */
package com.intellij.debugger.jdi;

import com.intellij.debugger.JavaDebuggerBundle;
import com.intellij.debugger.engine.DebuggerManagerThreadImpl;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluateExceptionUtil;
import com.intellij.debugger.engine.jdi.LocalVariableProxy;
import com.intellij.debugger.impl.DebuggerUtilsAsync;
import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.ThreeState;
import com.intellij.util.containers.ContainerUtil;
import com.sun.jdi.*;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

public class StackFrameProxyImpl extends JdiProxy implements StackFrameProxyEx {
  private static final Logger LOG = Logger.getInstance(StackFrameProxyImpl.class);
  public static final int FRAMES_BATCH_MAX = 20;
  private final ThreadReferenceProxyImpl myThreadProxy;
  private final int myFrameFromBottomIndex; // 1-based

  //caches
  private volatile int myFrameIndex = -1;
  private volatile StackFrame myStackFrame;
  private ObjectReference myThisReference;
  private ClassLoaderReference myClassLoader;
  private volatile ThreeState myIsObsolete = ThreeState.UNSURE;
  private Map<LocalVariable, Value> myAllValues;

  public StackFrameProxyImpl(@NotNull ThreadReferenceProxyImpl threadProxy, @NotNull StackFrame frame, int fromBottomIndex /* 1-based */) {
    super(threadProxy.getVirtualMachine());
    myThreadProxy = threadProxy;
    myFrameFromBottomIndex = fromBottomIndex;
    myStackFrame = frame;
  }

  public CompletableFuture<Boolean> isObsolete() throws EvaluateException {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    if (!getVirtualMachine().canRedefineClasses()) {
      return CompletableFuture.completedFuture(false);
    }
    checkValid();
    if (myIsObsolete != ThreeState.UNSURE) {
      return CompletableFuture.completedFuture(myIsObsolete.toBoolean());
    }
    return DebuggerUtilsAsync.method(location())
      .thenCompose(method -> {
        if (method == null) {
          myIsObsolete = ThreeState.YES;
          return CompletableFuture.completedFuture(true);
        }
        else {
          return DebuggerUtilsAsync.isObsolete(method).thenApply(res -> {
            myIsObsolete = ThreeState.fromBoolean(res);
            return res;
          });
        }
      })
      .exceptionally(throwable -> {
        Throwable exception = DebuggerUtilsAsync.unwrap(throwable);
        if (exception instanceof InternalException && ((InternalException)exception).errorCode() == JvmtiError.INVALID_METHODID) {
          myIsObsolete = ThreeState.YES;
          return true;
        }
        throw (RuntimeException)throwable;
      });
  }

  @TestOnly
  @Override
  public boolean isValid() {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    if (!super.isValid()) {
      return false;
    }
    try {
      if (myStackFrame != null) {
        myStackFrame.location(); //extra check if jdi frame is valid
      }
      return true;
    }
    catch (InvalidStackFrameException e) {
      return false;
    }
  }

  @Override
  protected void clearCaches() {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    if (LOG.isDebugEnabled()) {
      LOG.debug("caches cleared " + super.toString());
    }
    myFrameIndex = -1;
    myStackFrame = null;
    myIsObsolete = ThreeState.UNSURE;
    myThisReference = null;
    myClassLoader = null;
    myAllValues = null;
  }

  /**
   * Use with caution. Better access stackframe data through the Proxy's methods
   */

  @Override
  public StackFrame getStackFrame() throws EvaluateException {
    DebuggerManagerThreadImpl.assertIsManagerThread();

    checkValid();

    if (myStackFrame == null) {
      try {
        final ThreadReference threadRef = myThreadProxy.getThreadReference();
        int index = getFrameIndex();
        // batch get frames from 1 to FRAMES_BATCH_MAX
        // making this number very high does not help much because renderers invocation usually flush all caches
        if (index > 0 && index < FRAMES_BATCH_MAX) {
          myStackFrame = threadRef.frames(0, Math.min(myThreadProxy.frameCount(), FRAMES_BATCH_MAX)).get(index);
        }
        else {
          myStackFrame = threadRef.frame(index);
        }
      }
      catch (IndexOutOfBoundsException e) {
        throw new EvaluateException(e.getMessage(), e);
      }
      catch (ObjectCollectedException ignored) {
        throw EvaluateExceptionUtil.createEvaluateException(JavaDebuggerBundle.message("evaluation.error.thread.collected"));
      }
      catch (IncompatibleThreadStateException e) {
        throw EvaluateExceptionUtil.createEvaluateException(e);
      }
    }

    return myStackFrame;
  }

  public CompletableFuture<StackFrame> getStackFrameAsync() {
    DebuggerManagerThreadImpl.assertIsManagerThread();

    checkValid();

    if (myStackFrame == null) {
      ThreadReference threadRef = myThreadProxy.getThreadReference();
      return getFrameIndexAsync().thenCompose(index -> {
          // batch get frames from 1 to FRAMES_BATCH_MAX
          // making this number very high does not help much because renderers invocation usually flush all caches
          if (index > 0 && index < FRAMES_BATCH_MAX) {
            try {
              return DebuggerUtilsAsync.frames(threadRef, 0, Math.min(myThreadProxy.frameCount(), FRAMES_BATCH_MAX))
                .thenApply(frames -> myStackFrame = frames.get(index));
            }
            catch (EvaluateException e) {
              return CompletableFuture.failedFuture(e);
            }
          }
          else {
            return DebuggerUtilsAsync.frame(threadRef, index).thenApply(f -> myStackFrame = f);
          }
        })
        .exceptionally(throwable -> {
          if (DebuggerUtilsAsync.unwrap(throwable) instanceof ObjectCollectedException) {
            throw new CompletionException(EvaluateExceptionUtil.createEvaluateException(JavaDebuggerBundle.message("evaluation.error.thread.collected")));
          }
          throw (RuntimeException)throwable;
        });
    }

    return CompletableFuture.completedFuture(myStackFrame);
  }

  @Override
  public int getFrameIndex() throws EvaluateException {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    checkValid();
    if (myFrameIndex == -1) {
      int count = myThreadProxy.frameCount();

      if (myFrameFromBottomIndex > count) {
        throw EvaluateExceptionUtil.createEvaluateException(new IncompatibleThreadStateException());
      }

      myFrameIndex = count - myFrameFromBottomIndex;
    }
    return myFrameIndex;
  }

  public CompletableFuture<Integer> getFrameIndexAsync() {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    checkValid();
    if (myFrameIndex == -1) {
      return myThreadProxy.frameCountAsync().thenApply(count -> {
        if (myFrameFromBottomIndex > count) {
          throw new CompletionException(EvaluateExceptionUtil.createEvaluateException(new IncompatibleThreadStateException()));
        }
        myFrameIndex = count - myFrameFromBottomIndex;
        return myFrameIndex;
      });
    }
    return CompletableFuture.completedFuture(myFrameIndex);
  }

//  public boolean isProxiedFrameValid() {
//    if (myStackFrame != null) {
//      try {
//        myStackFrame.thread();
//        return true;
//      }
//      catch (InvalidStackFrameException e) {
//      }
//    }
//    return false;
//  }

  @Override
  public @NotNull VirtualMachineProxyImpl getVirtualMachine() {
    return (VirtualMachineProxyImpl)myTimer;
  }

  @Override
  public Location location() throws EvaluateException {
    InvalidStackFrameException error = null;
    for (int attempt = 0; attempt < 2; attempt++) {
      try {
        return getStackFrame().location();
      }
      catch (InvalidStackFrameException e) {
        error = e;
        clearCaches();
      }
    }
    throw new EvaluateException(error.getMessage(), error);
  }

  public CompletableFuture<Location> locationAsync() {
    return locationAsync(1);
  }

  private CompletableFuture<Location> locationAsync(int attempt) {
    return getStackFrameAsync()
      .thenCompose(frame -> {
        try {
          return CompletableFuture.completedFuture(frame.location());
        }
        catch (InvalidStackFrameException e) {
          if (attempt > 0) {
            return locationAsync(attempt - 1);
          }
          throw new CompletionException(new EvaluateException(e.getMessage(), e));
        }
      });
  }

  @Override
  public @NotNull ThreadReferenceProxyImpl threadProxy() {
    return myThreadProxy;
  }

  @Override
  public @NonNls String toString() {
    try {
      return "StackFrameProxyImpl: " + getStackFrame().toString();
    }
    catch (EvaluateException e) {
      return "StackFrameProxyImpl: " + e.getMessage() + "; frameFromBottom = " + myFrameFromBottomIndex + " threadName = " + threadProxy().name();
    }
  }

  @Override
  public @Nullable ObjectReference thisObject() throws EvaluateException {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    checkValid();
    try {
      for (int attempt = 0; attempt < 2; attempt++) {
        try {
          if (myThisReference == null) {
            myThisReference = getStackFrame().thisObject();
          }
          break;
        }
        catch (InvalidStackFrameException ignored) {
          clearCaches();
        }
      }
    }
    catch (InternalException e) {
      // suppress some internal errors caused by bugs in specific JDI implementations
      if (e.errorCode() != JvmtiError.INVALID_METHODID && e.errorCode() != JvmtiError.INVALID_SLOT) {
        throw EvaluateExceptionUtil.createEvaluateException(e);
      }
      else {
        LOG.info("Exception while getting this object", e);
      }
    }
    catch (IllegalArgumentException e) {
      LOG.info("Exception while getting this object", e);
    }
    catch (Exception e) {
      if (!getVirtualMachine().canBeModified()) { // do not care in read only vms
        LOG.debug(e);
      }
      else {
        throw e;
      }
    }
    return myThisReference;
  }

  public @NotNull List<LocalVariableProxyImpl> visibleVariables() throws EvaluateException {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    RuntimeException error = null;
    for (int attempt = 0; attempt < 2; attempt++) {
      try {
        final List<LocalVariable> list = getStackFrame().visibleVariables();
        final List<LocalVariableProxyImpl> locals = new ArrayList<>(list.size());
        for (LocalVariable localVariable : list) {
          LOG.assertTrue(localVariable != null);
          locals.add(new LocalVariableProxyImpl(this, localVariable));
        }
        return locals;
      }
      catch (InvalidStackFrameException | IllegalArgumentException e) {
        error = e;
        clearCaches();
      }
      catch (AbsentInformationException e) {
        throw EvaluateExceptionUtil.createEvaluateException(e);
      }
    }
    throw new EvaluateException(error.getMessage(), error);
  }

  @Override
  public LocalVariableProxyImpl visibleVariableByName(String name) throws EvaluateException {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    final LocalVariable variable = visibleVariableByNameInt(name);
    return variable != null ? new LocalVariableProxyImpl(this, variable) : null;
  }

  public @Nullable Value visibleValueByName(@NotNull String name) throws EvaluateException {
    LocalVariable variable = visibleVariableByNameInt(name);
    return variable != null ? getValue(new LocalVariableProxyImpl(this, variable)) : null;
  }

  protected LocalVariable visibleVariableByNameInt(String name) throws EvaluateException {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    InvalidStackFrameException error = null;
    for (int attempt = 0; attempt < 2; attempt++) {
      try {
        try {
          return getStackFrame().visibleVariableByName(name);
        }
        catch (InvalidStackFrameException e) {
          error = e;
          clearCaches();
        }
      }
      catch (InvalidStackFrameException | AbsentInformationException e) {
        throw EvaluateExceptionUtil.createEvaluateException(e);
      }
    }
    throw new EvaluateException(error.getMessage(), error);
  }

  @Override
  public Value getVariableValue(@NotNull LocalVariableProxy localVariable) throws EvaluateException {
    if (localVariable instanceof LocalVariableProxyImpl) {
      return getValue((LocalVariableProxyImpl)localVariable);
    }
    throw new EvaluateException("Variable doesn't belong to this frame: " + localVariable);
  }

  public Value getValue(LocalVariableProxyImpl localVariable) throws EvaluateException {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    Exception error = null;
    for (int attempt = 0; attempt < 2; attempt++) {
      try {
        LocalVariable variable = localVariable.getVariable();
        Map<LocalVariable, Value> values;
        try {
          values = getAllValues();
        }
        catch (InconsistentDebugInfoException ignored) {
          // possibly failed due to one of the variables, try getting one by one
          return getSingleValue(variable);
        }

        if (values.containsKey(variable)) {
          return values.get(variable);
        }
        else {
          return getSingleValue(variable);
        }
      }
      catch (InvalidStackFrameException e) {
        error = e;
        clearCaches();
      }
      catch (InconsistentDebugInfoException ignored) {
        clearCaches();
        throw EvaluateExceptionUtil.INCONSISTEND_DEBUG_INFO;
      }
      catch (InternalException e) {
        if (e.errorCode() == JvmtiError.INVALID_SLOT || e.errorCode() == JvmtiError.ABSENT_INFORMATION) {
          throw new EvaluateException(JavaDebuggerBundle.message("error.corrupt.debug.info", e.getMessage()), e);
        }
        else {
          throw e;
        }
      }
      catch (Exception e) {
        if (!getVirtualMachine().canBeModified()) { // do not care in read only vms
          LOG.debug(e);
          throw new EvaluateException("Debug data corrupted");
        }
        else {
          throw e;
        }
      }
    }
    throw new EvaluateException(error.getMessage(), error);
  }

  private Value getSingleValue(@NotNull LocalVariable variable) throws EvaluateException {
    return getStackFrame().getValue(variable);
  }

  public @NotNull List<Value> getArgumentValues() throws EvaluateException {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    InvalidStackFrameException error = null;
    for (int attempt = 0; attempt < 2; attempt++) {
      try {
        final StackFrame stackFrame = getStackFrame();
        return stackFrame != null ? ContainerUtil.notNullize(DebuggerUtilsEx.getArgumentValues(stackFrame)) : Collections.emptyList();
      }
      catch (InvalidStackFrameException e) {
        error = e;
        clearCaches();
      }
    }
    throw new EvaluateException(error.getMessage(), error);
  }

  private Map<LocalVariable, Value> getAllValues() throws EvaluateException {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    checkValid();
    if (myAllValues == null) {
      try {
        StackFrame stackFrame = getStackFrame();
        myAllValues = new HashMap<>(stackFrame.getValues(stackFrame.visibleVariables()));
      }
      catch (AbsentInformationException e) {
        throw EvaluateExceptionUtil.createEvaluateException(e);
      }
      catch (InternalException e) {
        // extra logging for IDEA-141270
        if (e.errorCode() == JvmtiError.INVALID_SLOT || e.errorCode() == JvmtiError.ABSENT_INFORMATION) {
          LOG.info(e);
          myAllValues = new HashMap<>();
        }
        else {
          throw e;
        }
      }
      catch (Exception e) {
        if (!getVirtualMachine().canBeModified()) { // do not care in read only vms
          LOG.debug(e);
          myAllValues = new HashMap<>();
        }
        else {
          throw e;
        }
      }
    }
    return myAllValues;
  }

  public void setValue(LocalVariableProxyImpl localVariable, Value value) throws EvaluateException, ClassNotLoadedException, InvalidTypeException {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    InvalidStackFrameException error = null;
    for (int attempt = 0; attempt < 2; attempt++) {
      try {
        final LocalVariable variable = localVariable.getVariable();
        final StackFrame stackFrame = getStackFrame();
        stackFrame.setValue(variable, value);
        if (myAllValues != null) {
          // update cached data if any
          // re-read the value just set from the stackframe to be 100% sure
          myAllValues.put(variable, stackFrame.getValue(variable));
        }
        return;
      }
      catch (InvalidStackFrameException e) {
        error = e;
        clearCaches();
      }
    }
    throw new EvaluateException(error.getMessage(), error);
  }

  @Override
  public int hashCode() {
    return 31 * myThreadProxy.hashCode() + myFrameFromBottomIndex;
  }


  @Override
  public boolean equals(final Object obj) {
    if (!(obj instanceof StackFrameProxyImpl frameProxy)) {
      return false;
    }
    if (frameProxy == this) return true;

    return (myFrameFromBottomIndex == frameProxy.myFrameFromBottomIndex) &&
           (myThreadProxy.equals(frameProxy.myThreadProxy));
  }

  public boolean isLocalVariableVisible(LocalVariableProxyImpl var) throws EvaluateException {
    try {
      return var.getVariable().isVisible(getStackFrame());
    }
    catch (IllegalArgumentException ignored) {
      // can be thrown if frame's method is different than variable's method
      return false;
    }
  }

  @Override
  public ClassLoaderReference getClassLoader() throws EvaluateException {
    if (myClassLoader == null) {
      myClassLoader = location().declaringType().classLoader();
    }
    return myClassLoader;
  }

  public boolean isBottom() {
    return myFrameFromBottomIndex == 1;
  }

  public int getIndexFromBottom() {
    return myFrameFromBottomIndex;
  }
}

