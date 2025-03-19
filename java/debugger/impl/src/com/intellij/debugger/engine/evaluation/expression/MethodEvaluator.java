// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

/*
 * Class MethodEvaluator
 * @author Jeka
 */
package com.intellij.debugger.engine.evaluation.expression;

import com.intellij.debugger.JavaDebuggerBundle;
import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.DebuggerUtils;
import com.intellij.debugger.engine.JVMName;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluateExceptionUtil;
import com.intellij.debugger.engine.evaluation.EvaluateRuntimeException;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.debugger.impl.DebuggerUtilsImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiPrimitiveType;
import com.intellij.psi.impl.PsiJavaParserFacadeImpl;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.jdi.MethodImpl;
import com.sun.jdi.*;
import one.util.streamex.StreamEx;

import java.util.ArrayList;
import java.util.List;

public class MethodEvaluator implements Evaluator {
  private static final Logger LOG = Logger.getInstance(MethodEvaluator.class);
  private final JVMName myClassName;
  private final JVMName myMethodSignature;
  private final String myMethodName;
  private final Evaluator[] myArgumentEvaluators;
  private final Evaluator myObjectEvaluator;
  private final boolean myMustBeVararg;

  public MethodEvaluator(Evaluator objectEvaluator,
                         JVMName className,
                         String methodName,
                         JVMName signature,
                         Evaluator[] argumentEvaluators) {
    this(objectEvaluator, className, methodName, signature, argumentEvaluators, false);
  }

  public MethodEvaluator(Evaluator objectEvaluator,
                         JVMName className,
                         String methodName,
                         JVMName signature,
                         Evaluator[] argumentEvaluators,
                         boolean mustBeVararg) {
    myObjectEvaluator = DisableGC.create(objectEvaluator);
    myClassName = className;
    myMethodName = methodName;
    myMethodSignature = signature;
    myArgumentEvaluators = argumentEvaluators;
    myMustBeVararg = mustBeVararg;
  }

  @Override
  public Object evaluate(EvaluationContextImpl context) throws EvaluateException {
    if (!context.getDebugProcess().isAttached()) {
      return null;
    }
    DebugProcessImpl debugProcess = context.getDebugProcess();

    final boolean requiresSuperObject = DisableGC.unwrap(myObjectEvaluator) instanceof SuperEvaluator;
    final Object object = myObjectEvaluator.evaluate(context);
    if (LOG.isDebugEnabled()) {
      LOG.debug("MethodEvaluator: object = " + object);
    }
    if (object == null) {
      throw EvaluateExceptionUtil.createEvaluateException(new NullPointerException());
    }
    if (!(object instanceof ObjectReference || isInvokableType(object))) {
      throw EvaluateExceptionUtil.createEvaluateException(JavaDebuggerBundle.message("evaluation.error.evaluating.method", myMethodName));
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
          JavaDebuggerBundle.message("evaluation.error.cannot.evaluate.qualifier", myMethodName))
        );
      }
      final String signature = myMethodSignature != null ? myMethodSignature.getName(debugProcess) : null;

      if (requiresSuperObject && (referenceType instanceof ClassType)) {
        referenceType = ((ClassType)referenceType).superclass();
        String className = myClassName != null ? myClassName.getName(debugProcess) : null;
        if (referenceType == null || (className != null && !className.equals(referenceType.name()))) {
          referenceType = debugProcess.findClass(context, className, context.getClassLoader());
        }
      }

      Method jdiMethod = null;
      if (signature == null) {
        // we know nothing about expected method's signature, so trying to match my method name and parameter count
        // dummy matching, may be improved with types matching later
        // IMPORTANT! using argumentTypeNames() instead of argumentTypes() to avoid type resolution inside JDI, which may be time-consuming
        //noinspection SSBasedInspection
        List<Method> matchingMethods =
          StreamEx.of(referenceType.methodsByName(myMethodName)).filter(m -> m.argumentTypeNames().size() == args.size()).toList();
        if (matchingMethods.size() == 1) {
          jdiMethod = matchingMethods.get(0);
        }
        else if (matchingMethods.size() > 1) {
          jdiMethod = ContainerUtil.find(matchingMethods, m -> matchArgs(m, args));
        }
      }
      if (jdiMethod == null) {
        jdiMethod = DebuggerUtils.findMethod(referenceType, myMethodName, signature);
      }
      if (jdiMethod == null) {
        throw EvaluateExceptionUtil.createEvaluateException(JavaDebuggerBundle.message("evaluation.error.no.instance.method", myMethodName));
      }
      if (myMustBeVararg && !jdiMethod.isVarArgs() && ContainerUtil.getLastItem(jdiMethod.argumentTypes()) instanceof ArrayType) {
        // this is a workaround for jdk bugs when bridge or proxy methods do not have ACC_VARARGS flags
        // see IDEA-129869 and IDEA-202380
        MethodImpl.handleVarArgs(jdiMethod, args);
      }
      if (signature == null) { // runtime conversions
        argsConversions(jdiMethod, args, context);
      }

      // Static methods
      if (isInvokableType(object)) {
        if (isInvokableType(referenceType)) {
          if (jdiMethod.isStatic()) {
            if (referenceType instanceof ClassType) {
              return debugProcess.invokeMethod(context, (ClassType)referenceType, jdiMethod, args);
            }
            else {
              return debugProcess.invokeMethod(context, (InterfaceType)referenceType, jdiMethod, args);
            }
          }
        }
        throw EvaluateExceptionUtil.createEvaluateException(JavaDebuggerBundle.message(
          "evaluation.error.no.static.method", DebuggerUtilsEx.methodName(referenceType.name(), myMethodName, signature)));
      }

      // object should be an ObjectReference
      final ObjectReference objRef = (ObjectReference)object;

      if (requiresSuperObject) {
        return debugProcess.invokeInstanceMethod(context, objRef, jdiMethod, args, ObjectReference.INVOKE_NONVIRTUAL);
      }
      return debugProcess.invokeMethod(context, objRef, jdiMethod, args);
    }
    catch (Exception e) {
      LOG.debug(e);
      throw EvaluateExceptionUtil.createEvaluateException(e);
    }
  }

  private static boolean matchArgs(Method m, List<Value> args) {
    try {
      List<Type> argumentTypes = m.argumentTypes();
      for (int i = 0; i < argumentTypes.size(); i++) {
        Type expectedArgType = argumentTypes.get(i);
        Type argType = args.get(i).type();
        if (expectedArgType.equals(argType)) {
          continue;
        }
        if (expectedArgType instanceof ReferenceType) {
          if (argType == null) {
            continue;
          }
          else if (argType instanceof PrimitiveType) {
            // TODO: boxing-unboxing
          }
          else if (argType instanceof ReferenceType &&
                   DebuggerUtilsImpl.instanceOf((ReferenceType)argType, (ReferenceType)expectedArgType)) {
            continue;
          }
        }
        return false;
      }
    }
    catch (ClassNotLoadedException ignored) {
      return false;
    }
    return true;
  }

  private static void argsConversions(Method jdiMethod, List<Value> args, EvaluationContextImpl context) throws EvaluateException {
    if (!jdiMethod.isVarArgs()) { // totally skip varargs for now
      List<String> typeNames = jdiMethod.argumentTypeNames();
      int size = typeNames.size();
      if (size == args.size()) {
        for (int i = 0; i < size; i++) {
          Value arg = args.get(i);
          PsiPrimitiveType primitiveType = PsiJavaParserFacadeImpl.getPrimitiveType(typeNames.get(i));
          if (primitiveType == null && arg.type() instanceof PrimitiveType) {
            args.set(i, (Value)BoxingEvaluator.box(arg, context));
          }
          else if (primitiveType != null && !(arg.type() instanceof PrimitiveType)) {
            args.set(i, (Value)UnBoxingEvaluator.unbox(arg, context));
          }
        }
      }
    }
  }

  private static boolean isInvokableType(Object type) {
    return type instanceof ClassType || type instanceof InterfaceType;
  }

  @Override
  public String toString() {
    return "call " + myMethodName;
  }
}
