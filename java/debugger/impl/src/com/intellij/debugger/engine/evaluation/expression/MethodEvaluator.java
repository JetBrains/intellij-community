// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

/*
 * Class MethodEvaluator
 * @author Jeka
 */
package com.intellij.debugger.engine.evaluation.expression;

import com.intellij.Patches;
import com.intellij.debugger.DebuggerBundle;
import com.intellij.debugger.engine.DebugProcess;
import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.DebuggerUtils;
import com.intellij.debugger.engine.JVMName;
import com.intellij.debugger.engine.evaluation.*;
import com.intellij.debugger.impl.ClassLoadingUtils;
import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.debugger.jdi.VirtualMachineProxyImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.rt.debugger.DefaultMethodInvoker;
import com.sun.jdi.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MethodEvaluator implements Evaluator {
  private static final Logger LOG = Logger.getInstance("#com.intellij.debugger.engine.evaluation.expression.MethodEvaluator");
  private final JVMName myClassName;
  private final JVMName myMethodSignature;
  private final String myMethodName;
  private final Evaluator[] myArgumentEvaluators;
  private final Evaluator myObjectEvaluator;
  private final boolean myCheckDefaultInterfaceMethod;
  private final boolean myMustBeVararg;

  public MethodEvaluator(Evaluator objectEvaluator,
                         JVMName className,
                         String methodName,
                         JVMName signature,
                         Evaluator[] argumentEvaluators) {
    this(objectEvaluator, className, methodName, signature, argumentEvaluators, false, false);
  }

  public MethodEvaluator(Evaluator objectEvaluator,
                         JVMName className,
                         String methodName,
                         JVMName signature,
                         Evaluator[] argumentEvaluators,
                         boolean checkDefaultInterfaceMethod,
                         boolean mustBeVararg) {
    myObjectEvaluator = DisableGC.create(objectEvaluator);
    myClassName = className;
    myMethodName = methodName;
    myMethodSignature = signature;
    myArgumentEvaluators = argumentEvaluators;
    myCheckDefaultInterfaceMethod = checkDefaultInterfaceMethod;
    myMustBeVararg = mustBeVararg;
  }

  @Override
  public Object evaluate(EvaluationContextImpl context) throws EvaluateException {
    if (!context.getDebugProcess().isAttached()) {
      return null;
    }
    DebugProcessImpl debugProcess = context.getDebugProcess();

    final boolean requiresSuperObject =
      myObjectEvaluator instanceof SuperEvaluator ||
      (myObjectEvaluator instanceof DisableGC && ((DisableGC)myObjectEvaluator).getDelegate() instanceof SuperEvaluator);

    final Object object = myObjectEvaluator.evaluate(context);
    if (LOG.isDebugEnabled()) {
      LOG.debug("MethodEvaluator: object = " + object);
    }
    if (object == null) {
      throw EvaluateExceptionUtil.createEvaluateException(new NullPointerException());
    }
    if (!(object instanceof ObjectReference || isInvokableType(object))) {
      throw EvaluateExceptionUtil.createEvaluateException(DebuggerBundle.message("evaluation.error.evaluating.method", myMethodName));
    }
    List<Value> args = new ArrayList<>(myArgumentEvaluators.length);
    for (Evaluator evaluator : myArgumentEvaluators) {
      args.add((Value)evaluator.evaluate(context));
    }
    try {
      ReferenceType referenceType = null;

      if (object instanceof ObjectReference) {
        // it seems that if we have an object of the class, the class must be ready, so no need to use findClass here
        referenceType = ((ObjectReference)object).referenceType();
      }
      else if (isInvokableType(object)) {
        referenceType = (ReferenceType)object;
      }

      if (referenceType == null) {
        throw new EvaluateRuntimeException(EvaluateExceptionUtil.createEvaluateException(
          DebuggerBundle.message("evaluation.error.cannot.evaluate.qualifier", myMethodName))
        );
      }
      final String signature = myMethodSignature != null ? myMethodSignature.getName(debugProcess) : null;
      final String methodName = DebuggerUtilsEx.methodName(referenceType.name(), myMethodName, signature);
      if (isInvokableType(object)) {
        if (isInvokableType(referenceType)) {
          Method jdiMethod = DebuggerUtils.findMethod(referenceType, myMethodName, signature);
          if (jdiMethod != null && jdiMethod.isStatic()) {
            if (referenceType instanceof ClassType) {
              return debugProcess.invokeMethod(context, (ClassType)referenceType, jdiMethod, args);
            }
            else {
              return debugProcess.invokeMethod(context, (InterfaceType)referenceType, jdiMethod, args);
            }
          }
        }
        throw EvaluateExceptionUtil.createEvaluateException(DebuggerBundle.message("evaluation.error.no.static.method", methodName));
      }
      // object should be an ObjectReference
      final ObjectReference objRef = (ObjectReference)object;
      ReferenceType _refType = referenceType;
      if (requiresSuperObject && (referenceType instanceof ClassType)) {
        _refType = ((ClassType)referenceType).superclass();
        String className = myClassName != null ? myClassName.getName(debugProcess) : null;
        if (_refType == null || (className != null && !className.equals(_refType.name()))) {
          _refType = debugProcess.findClass(context, className, context.getClassLoader());
        }
      }
      Method jdiMethod = DebuggerUtils.findMethod(_refType, myMethodName, signature);
      if (signature == null) {
        // we know nothing about expected method's signature, so trying to match my method name and parameter count
        // dummy matching, may be improved with types matching later
        // IMPORTANT! using argumentTypeNames() instead of argumentTypes() to avoid type resolution inside JDI, which may be time-consuming
        if (jdiMethod == null || jdiMethod.argumentTypeNames().size() != args.size()) {
          for (Method method : _refType.methodsByName(myMethodName)) {
            if (method.argumentTypeNames().size() == args.size()) {
              jdiMethod = method;
              break;
            }
          }
        }
      }
      else if (myMustBeVararg && jdiMethod != null && !jdiMethod.isVarArgs() && jdiMethod.isBridge()) {
        // see IDEA-129869, avoid bridge methods for varargs
        int retTypePos = signature.lastIndexOf(")");
        if (retTypePos >= 0) {
          String signatureNoRetType = signature.substring(0, retTypePos + 1);
          for (Method method : _refType.visibleMethods()) {
            if (method.name().equals(myMethodName) &&
                method.signature().startsWith(signatureNoRetType) &&
                !method.isBridge() &&
                !method.isAbstract()) {
              jdiMethod = method;
              break;
            }
          }
        }
      }
      if (jdiMethod == null) {
        throw EvaluateExceptionUtil.createEvaluateException(DebuggerBundle.message("evaluation.error.no.instance.method", methodName));
      }
      if (requiresSuperObject) {
        return debugProcess.invokeInstanceMethod(context, objRef, jdiMethod, args, ObjectReference.INVOKE_NONVIRTUAL);
      }
      // fix for default methods in interfaces, see IDEA-124066
      if (Patches.JDK_BUG_ID_8042123 && myCheckDefaultInterfaceMethod && jdiMethod.declaringType() instanceof InterfaceType) {
        try {
          return invokeDefaultMethod(debugProcess, context, objRef, myMethodName);
        }
        catch (EvaluateException e) {
          LOG.info(e);
        }
      }
      return debugProcess.invokeMethod(context, objRef, jdiMethod, args);
    }
    catch (Exception e) {
      LOG.debug(e);
      throw EvaluateExceptionUtil.createEvaluateException(e);
    }
  }

  private static boolean isInvokableType(Object type) {
    return type instanceof ClassType || type instanceof InterfaceType;
  }

  // only methods without arguments for now
  private static Value invokeDefaultMethod(DebugProcess debugProcess, EvaluationContext evaluationContext,
                                           Value obj, String name)
    throws EvaluateException {
    ClassType invokerClass = ClassLoadingUtils.getHelperClass(DefaultMethodInvoker.class, evaluationContext);

    if (invokerClass != null) {
      List<Method> methods = invokerClass.methodsByName("invoke");
      if (!methods.isEmpty()) {
        return debugProcess.invokeMethod(evaluationContext, invokerClass, methods.get(0),
               Arrays.asList(obj, ((VirtualMachineProxyImpl)debugProcess.getVirtualMachineProxy()).mirrorOf(name)));
      }
    }
    return null;
  }

  @Override
  public String toString() {
    return "call " + myMethodName;
  }
}
