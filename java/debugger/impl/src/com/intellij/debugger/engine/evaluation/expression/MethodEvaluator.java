// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

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
import com.intellij.debugger.engine.evaluation.EvaluationContext;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.debugger.impl.DebuggerUtilsImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiPrimitiveType;
import com.intellij.psi.impl.PsiJavaParserFacadeImpl;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.jdi.MethodImpl;
import com.sun.jdi.ArrayReference;
import com.sun.jdi.ArrayType;
import com.sun.jdi.ClassNotLoadedException;
import com.sun.jdi.ClassType;
import com.sun.jdi.IncompatibleThreadStateException;
import com.sun.jdi.InterfaceType;
import com.sun.jdi.InvalidTypeException;
import com.sun.jdi.InvocationException;
import com.sun.jdi.Method;
import com.sun.jdi.ObjectReference;
import com.sun.jdi.PrimitiveType;
import com.sun.jdi.ReferenceType;
import com.sun.jdi.Type;
import com.sun.jdi.Value;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;

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
  private final boolean myLastArgumentIsNotArray;

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
                         boolean mustBeVararg,
                         boolean lastArgumentIsNotArray) {
    myObjectEvaluator = DisableGC.create(objectEvaluator);
    myClassName = className;
    myMethodName = methodName;
    myMethodSignature = signature;
    myArgumentEvaluators = argumentEvaluators;
    myMustBeVararg = mustBeVararg;
    myLastArgumentIsNotArray = lastArgumentIsNotArray;
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

      if (myMustBeVararg || jdiMethod.isVarArgs()) {
        // we have to call it for bridge or proxy methods that do not have ACC_VARARGS flags
        // see IDEA-129869 and IDEA-202380
        handleVarargs(jdiMethod, args, context);
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

  /**
   * This method is an imroved version of {@link MethodImpl#handleVarArgs(Method, List)}:
   * <ul>
   * <li>creation of arrays is done through {@link DebuggerUtilsEx#mirrorOfArray(ArrayType, int, EvaluationContext)} to avoid
   * an immediate result collection</li>
   * <li>wrapping of null vararg value into an array depending on the argument type</li>
   * <li>load vararg parameter type if it is not yet loaded</li>
   * </ul>
   */
  private void handleVarargs(@NotNull Method jdiMethod, @NotNull List<Value> args, @NotNull EvaluationContextImpl context)
    throws ClassNotLoadedException, InvalidTypeException, EvaluateException {
    int paramCount = jdiMethod.argumentTypeNames().size();
    ArrayType lastParamType = getLastParameterArrayType(jdiMethod, context);
    int argCount = args.size();
    if (argCount < paramCount - 1) {
      return;
    }
    if (argCount == paramCount - 1) {
      args.add(DebuggerUtilsEx.mirrorOfArray(lastParamType, 0, context));
      return;
    }
    Value nthArgValue = args.get(paramCount - 1);
    if (nthArgValue == null && argCount == paramCount) {
      if (myLastArgumentIsNotArray) {
        args.set(paramCount - 1, DebuggerUtilsEx.mirrorOfArray(lastParamType, 1, context));
      }
      return;
    }
    Type nthArgType = (nthArgValue == null) ? null : nthArgValue.type();
    if (nthArgType instanceof ArrayType arrayType) {
      if (argCount == paramCount && DebuggerUtilsImpl.instanceOf(arrayType, lastParamType)) {
        return;
      }
    }

    int count = argCount - paramCount + 1;
    ArrayReference argArray = DebuggerUtilsEx.mirrorOfArray(lastParamType, count, context);

    argArray.setValues(0, args, paramCount - 1, count);
    args.set(paramCount - 1, argArray);

    if (argCount > paramCount) {
      args.subList(paramCount, argCount).clear();
    }
  }

  private static ArrayType getLastParameterArrayType(@NotNull Method jdiMethod, @NotNull EvaluationContextImpl context)
    throws ClassNotLoadedException, EvaluateException, InvalidTypeException {
    int paramCount = jdiMethod.argumentTypeNames().size();
    if (jdiMethod instanceof MethodImpl methodImpl) {
      try {
        String paramSignature = methodImpl.argumentSignatures().get(paramCount - 1);
        return (ArrayType)methodImpl.findType(paramSignature);
      }
      catch (ClassNotLoadedException e) {
        try {
          return (ArrayType)context.getDebugProcess().loadClass(context, e, jdiMethod.declaringType().classLoader());
        }
        catch (IncompatibleThreadStateException | InvocationException ex) {
          throw EvaluateExceptionUtil.createEvaluateException(ex);
        }
      }
    }
    else {
      return (ArrayType)jdiMethod.argumentTypes().get(paramCount - 1);
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
