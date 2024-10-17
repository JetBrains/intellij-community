// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.engine;

import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContext;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.engine.requests.RequestManagerImpl;
import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.debugger.impl.DebuggerUtilsImpl;
import com.intellij.debugger.jdi.ClassesByNameProvider;
import com.intellij.debugger.jdi.StackFrameProxyImpl;
import com.intellij.debugger.jdi.VirtualMachineProxyImpl;
import com.intellij.debugger.memory.utils.StackFrameItem;
import com.intellij.debugger.requests.ClassPrepareRequestor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.registry.Registry;
import com.sun.jdi.*;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@ApiStatus.Experimental
public final class CollectionBreakpointUtils {
  private static final Logger LOG = Logger.getInstance(CollectionBreakpointUtils.class);

  private static final String OBJECT_TYPE = "Ljava/lang/Object;";
  private static final String STRING_TYPE = "Ljava/lang/String;";

  private static final String INSTRUMENTOR_CLS_NAME = "com.intellij.rt.debugger.agent.CollectionBreakpointInstrumentor";
  private static final String STORAGE_CLASS_NAME = "com.intellij.rt.debugger.agent.CollectionBreakpointStorage";

  private static final String COLLECTION_MODIFICATION_INFO_CLASS_NAME = STORAGE_CLASS_NAME + "$" + "CollectionModificationInfo";

  private static final String ENABLE_DEBUG_MODE_FIELD = "DEBUG";
  private static final String ENABLE_HISTORY_SAVING_FIELD = "ENABLED";

  private static final String GET_FIELD_MODIFICATIONS_METHOD_NAME = "getFieldModifications";
  private static final String GET_FIELD_MODIFICATIONS_METHOD_DESC = "(" + STRING_TYPE + STRING_TYPE + OBJECT_TYPE + ")[" + OBJECT_TYPE;
  private static final String GET_COLLECTION_MODIFICATIONS_METHOD_NAME = "getCollectionModifications";
  private static final String GET_COLLECTION_MODIFICATIONS_METHOD_DESC = "(" + OBJECT_TYPE + ")[" + OBJECT_TYPE;
  private static final String GET_COLLECTION_STACK_METHOD_NAME = "getStack";
  private static final String GET_COLLECTION_STACK_METHOD_DESC = "(" + OBJECT_TYPE + "I" + ")" + STRING_TYPE;
  private static final String GET_FIELD_STACK_METHOD_NAME = "getStack";
  private static final String GET_FIELD_STACK_METHOD_DESC = "(" + STRING_TYPE + STRING_TYPE + OBJECT_TYPE + "I" + ")" + STRING_TYPE;
  private static final String GET_ELEMENT_METHOD_NAME = "getElement";
  private static final String GET_ELEMENT_METHOD_DESC = "()" + OBJECT_TYPE;
  private static final String IS_ADDITION_METHOD_NAME = "isAddition";
  private static final String IS_ADDITION_METHOD_DESC = "()Z";

  public static void setupCollectionBreakpointAgent(DebugProcessImpl debugProcess) {
    if (Registry.is("debugger.collection.breakpoint.agent.debug")) {
      enableDebugMode(debugProcess);
    }
  }

  private static void enableDebugMode(DebugProcessImpl debugProcess) {
    try {
      setClassBooleanField(debugProcess, INSTRUMENTOR_CLS_NAME, ENABLE_DEBUG_MODE_FIELD, true);
    }
    catch (Exception e) {
      LOG.warn("Error setting collection breakpoint agent debug mode", e);
    }
  }

  public static void setCollectionHistorySavingEnabled(DebugProcessImpl debugProcess, boolean enabled) {
    try {
      setClassBooleanField(debugProcess, STORAGE_CLASS_NAME, ENABLE_HISTORY_SAVING_FIELD, enabled);
    }
    catch (Exception e) {
      LOG.warn("Error setting collection history saving enabled", e);
    }
  }

  private static void setClassBooleanField(DebugProcessImpl debugProcess,
                                           String clsName,
                                           String fieldName,
                                           boolean value) throws EvaluateException {
    final RequestManagerImpl requestsManager = debugProcess.getRequestsManager();
    ClassPrepareRequestor requestor = new ClassPrepareRequestor() {
      @Override
      public void processClassPrepare(DebugProcess debuggerProcess, ReferenceType referenceType) {
        try {
          requestsManager.deleteRequest(this);
          Field field = DebuggerUtils.findField(referenceType, fieldName);
          Value trueValue = debugProcess.getVirtualMachineProxy().mirrorOf(value);
          ((ClassType)referenceType).setValue(field, trueValue);
        }
        catch (Exception e) {
          LOG.warn("Error setting field " + fieldName + " of class " + clsName, e);
        }
      }
    };

    requestsManager.callbackOnPrepareClasses(requestor, clsName);

    ClassType captureClass = (ClassType)debugProcess.findClass(null, clsName, null);
    if (captureClass != null) {
      requestor.processClassPrepare(debugProcess, captureClass);
    }
  }

  private static VirtualMachineProxyImpl getVirtualMachine(SuspendContextImpl context) {
    StackFrameProxyImpl frameProxy = context.getFrameProxy();
    return frameProxy != null ? frameProxy.getVirtualMachine() : null;
  }

  @NotNull
  public static List<Value> getFieldModificationsHistory(SuspendContextImpl context,
                                                         String fieldName,
                                                         String clsName,
                                                         @Nullable Value clsInstance) {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    VirtualMachineProxyImpl virtualMachineProxy = getVirtualMachine(context);
    if (virtualMachineProxy == null) {
      return Collections.emptyList();
    }

    Value clsNameRef = virtualMachineProxy.mirrorOf(clsName);
    Value fieldNameRef = virtualMachineProxy.mirrorOf(fieldName);

    Value result = invokeStorageMethod(context.getDebugProcess(), context,
                                       GET_FIELD_MODIFICATIONS_METHOD_NAME,
                                       GET_FIELD_MODIFICATIONS_METHOD_DESC,
                                       toList(clsNameRef, fieldNameRef, clsInstance));

    if (result instanceof ArrayReference) {
      return ((ArrayReference)result).getValues();
    }

    return Collections.emptyList();
  }

  @NotNull
  public static List<StackFrameItem> getFieldModificationStack(SuspendContextImpl context,
                                                               String fieldName,
                                                               String clsName,
                                                               @Nullable Value collectionInstance,
                                                               IntegerValue modificationIndex) {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    VirtualMachineProxyImpl virtualMachineProxy = getVirtualMachine(context);
    if (virtualMachineProxy == null) {
      return Collections.emptyList();
    }

    Value clsNameRef = virtualMachineProxy.mirrorOf(clsName);
    Value fieldNameRef = virtualMachineProxy.mirrorOf(fieldName);

    Value result = invokeStorageMethod(context.getDebugProcess(), context,
                                       GET_FIELD_STACK_METHOD_NAME,
                                       GET_FIELD_STACK_METHOD_DESC,
                                       toList(clsNameRef, fieldNameRef, collectionInstance, modificationIndex));

    String message = result instanceof StringReference ? ((StringReference)result).value() : "";

    return readStackItems(context.getDebugProcess(), message, virtualMachineProxy);
  }

  private static List<StackFrameItem> readStackItems(DebugProcessImpl debugProcess,
                                                     String message,
                                                     VirtualMachineProxyImpl virtualMachineProxy) {
    List<StackFrameItem> items = new ArrayList<>();
    ClassesByNameProvider classesByName = ClassesByNameProvider.createCache(virtualMachineProxy.allClasses());
    try (DataInputStream dis = new DataInputStream(new ByteArrayInputStream(message.getBytes(StandardCharsets.ISO_8859_1)))) {
      while (dis.available() > 0) {
        String className = dis.readUTF();
        String methodName = dis.readUTF();
        int line = dis.readInt();
        Location location = DebuggerUtilsEx.findOrCreateLocation(debugProcess, classesByName, className, methodName, line);
        StackFrameItem item = new StackFrameItem(location, null);
        items.add(item);
      }
    }
    catch (Exception e) {
      DebuggerUtilsImpl.logError(e);
    }
    return items;
  }

  @NotNull
  public static List<Value> getCollectionModificationsHistory(SuspendContextImpl context, Value collectionInstance) {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    VirtualMachineProxyImpl virtualMachineProxy = getVirtualMachine(context);
    if (virtualMachineProxy == null) {
      return Collections.emptyList();
    }

    Value collectionModifications = invokeStorageMethod(context.getDebugProcess(), context,
                                                        GET_COLLECTION_MODIFICATIONS_METHOD_NAME,
                                                        GET_COLLECTION_MODIFICATIONS_METHOD_DESC,
                                                        Collections.singletonList(collectionInstance));

    if (collectionModifications instanceof ArrayReference) {
      return ((ArrayReference)collectionModifications).getValues();
    }

    return Collections.emptyList();
  }

  public static List<StackFrameItem> getCollectionModificationStack(SuspendContextImpl context,
                                                                    @Nullable Value collectionInstance,
                                                                    IntegerValue modificationIndex) {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    VirtualMachineProxyImpl virtualMachineProxy = getVirtualMachine(context);
    if (virtualMachineProxy == null) {
      return Collections.emptyList();
    }

    Value result = invokeStorageMethod(context.getDebugProcess(), context,
                                       GET_COLLECTION_STACK_METHOD_NAME,
                                       GET_COLLECTION_STACK_METHOD_DESC,
                                       toList(collectionInstance, modificationIndex));

    String message = result instanceof StringReference ? ((StringReference)result).value() : "";

    return readStackItems(context.getDebugProcess(), message, virtualMachineProxy);
  }

  private static List<Value> toList(Value... elements) {
    List<Value> list = new ArrayList<>();
    Collections.addAll(list, elements);
    return list;
  }

  @Nullable
  public static Pair<ObjectReference, BooleanValue> getCollectionModificationInfo(DebugProcessImpl debugProcess,
                                                                                  EvaluationContext evaluationContext,
                                                                                  ObjectReference collectionInstance) {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    ClassType cls = getClass(debugProcess, evaluationContext, COLLECTION_MODIFICATION_INFO_CLASS_NAME);
    if (cls != null) {
      Method getElementMethod = DebuggerUtils.findMethod(cls, GET_ELEMENT_METHOD_NAME, GET_ELEMENT_METHOD_DESC);
      Method isAdditionMethod = DebuggerUtils.findMethod(cls, IS_ADDITION_METHOD_NAME, IS_ADDITION_METHOD_DESC);
      if (getElementMethod == null || isAdditionMethod == null) {
        return null;
      }
      try {
        Value element =
          debugProcess.invokeInstanceMethod(evaluationContext, collectionInstance, getElementMethod, Collections.emptyList(), 0);
        Value isAddition =
          debugProcess.invokeInstanceMethod(evaluationContext, collectionInstance, isAdditionMethod, Collections.emptyList(), 0);
        if (element instanceof ObjectReference && isAddition instanceof BooleanValue) {
          return new Pair<>((ObjectReference)element, (BooleanValue)isAddition);
        }
      }
      catch (EvaluateException e) {
        DebuggerUtilsImpl.logError(e);
      }
    }
    return null;
  }

  public static Value invokeInstrumentorMethod(DebugProcessImpl debugProcess,
                                               SuspendContextImpl context,
                                               String methodName,
                                               String methodDesc,
                                               List<Value> args) {
    return invokeMethod(debugProcess, context, INSTRUMENTOR_CLS_NAME, methodName, methodDesc, args);
  }

  public static Value invokeStorageMethod(DebugProcessImpl debugProcess,
                                          SuspendContextImpl context,
                                          String methodName,
                                          String methodDesc,
                                          List<Value> args) {
    return invokeMethod(debugProcess, context, STORAGE_CLASS_NAME, methodName, methodDesc, args);
  }

  private static ClassType getClass(DebugProcessImpl debugProcess, @Nullable EvaluationContext evalContext, String clsName) {
    try {
      return (ClassType)debugProcess.findClass(evalContext, clsName, null);
    }
    catch (EvaluateException e) {
      return null;
    }
  }

  public static ClassType getInstrumentorClass(DebugProcessImpl debugProcess, @Nullable EvaluationContextImpl evalContext) {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    return getClass(debugProcess, evalContext, INSTRUMENTOR_CLS_NAME);
  }

  public static ClassType getStorageClass(DebugProcessImpl debugProcess, @Nullable EvaluationContextImpl evalContext) {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    return getClass(debugProcess, evalContext, STORAGE_CLASS_NAME);
  }

  private static Value invokeMethod(DebugProcessImpl debugProcess,
                                    SuspendContextImpl context,
                                    String clsName,
                                    String methodName,
                                    String methodDesc,
                                    List<Value> args) {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    EvaluationContextImpl evalContext = new EvaluationContextImpl(context, context.getFrameProxy());
    evalContext = evalContext.withAutoLoadClasses(false);
    try {
      ClassType cls = getClass(debugProcess, evalContext, clsName);
      if (cls == null) {
        return null;
      }
      Method method = DebuggerUtils.findMethod(cls, methodName, methodDesc);
      if (method != null) {
        return debugProcess.invokeMethod(evalContext, cls, method, args);
      }
    }
    catch (EvaluateException e) {
      DebuggerUtilsImpl.logError(e);
    }
    return null;
  }
}
