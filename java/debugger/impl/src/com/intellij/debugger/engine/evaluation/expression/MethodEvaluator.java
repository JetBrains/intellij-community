/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*
 * Class MethodEvaluator
 * @author Jeka
 */
package com.intellij.debugger.engine.evaluation.expression;

import com.intellij.debugger.DebuggerBundle;
import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.JVMName;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluateExceptionUtil;
import com.intellij.debugger.engine.evaluation.EvaluateRuntimeException;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.openapi.diagnostic.Logger;
import com.sun.jdi.ClassType;
import com.sun.jdi.Method;
import com.sun.jdi.ObjectReference;
import com.sun.jdi.ReferenceType;

import java.util.ArrayList;
import java.util.List;

public class MethodEvaluator implements Evaluator {
  private static final Logger LOG = Logger.getInstance("#com.intellij.debugger.engine.evaluation.expression.MethodEvaluator");
  private final JVMName myClassName;
  private final JVMName myMethodSignature;
  private final String myMethodName;
  private final Evaluator[] myArgumentEvaluators;
  private final Evaluator myObjectEvaluator;

  public MethodEvaluator(Evaluator objectEvaluator, JVMName className, String methodName, JVMName signature, Evaluator[] argumentEvaluators) {
    myObjectEvaluator = new DisableGC(objectEvaluator);
    myClassName = className;
    myMethodName = methodName;
    myMethodSignature = signature;
    myArgumentEvaluators = argumentEvaluators;
  }

  public Modifier getModifier() {
    return null;
  }

  public Object evaluate(EvaluationContextImpl context) throws EvaluateException {
    if(!context.getDebugProcess().isAttached()) return null;
    DebugProcessImpl debugProcess = context.getDebugProcess();
    
    final boolean requiresSuperObject = 
      myObjectEvaluator instanceof SuperEvaluator || 
      (myObjectEvaluator instanceof DisableGC && ((DisableGC)myObjectEvaluator).getDelegate() instanceof SuperEvaluator);
    
    final Object object = myObjectEvaluator.evaluate(context);
    if (LOG.isDebugEnabled()) {
      LOG.debug("MethodEvaluator: object = " + object);
    }
    if(object == null) {
      throw EvaluateExceptionUtil.createEvaluateException(new NullPointerException());
    }
    if (!(object instanceof ObjectReference || object instanceof ClassType)) {
      throw EvaluateExceptionUtil.createEvaluateException(DebuggerBundle.message("evaluation.error.evaluating.method", myMethodName));
    }
    List args = new ArrayList(myArgumentEvaluators.length);
    for (Evaluator evaluator : myArgumentEvaluators) {
      args.add(evaluator.evaluate(context));
    }
    try {
      ReferenceType referenceType = null;

      if(object instanceof ObjectReference) {
        final ReferenceType qualifierType = ((ObjectReference)object).referenceType();
        referenceType = debugProcess.findClass(context, qualifierType.name(), qualifierType.classLoader());
      }
      else if(object instanceof ClassType) {
        final ClassType qualifierType = (ClassType)object;
        referenceType = debugProcess.findClass(context, qualifierType.name(), context.getClassLoader());
      }
      else {
        final String className = myClassName != null? myClassName.getName(debugProcess) : null;
        if (className != null) {
          referenceType = debugProcess.findClass(context, className, context.getClassLoader());
        }
      }
      
      if (referenceType == null) {
        throw new EvaluateRuntimeException(EvaluateExceptionUtil.createEvaluateException(
          DebuggerBundle.message("evaluation.error.cannot.evaluate.qualifier", myMethodName))
        );
      }
      final String signature = myMethodSignature != null ? myMethodSignature.getName(debugProcess) : null;
      final String methodName = DebuggerUtilsEx.methodName(referenceType.name(), myMethodName, signature);
      if (object instanceof ClassType) {
        if(referenceType instanceof ClassType) {
          Method jdiMethod;
          if(myMethodSignature != null) {
            jdiMethod = ((ClassType)referenceType).concreteMethodByName(myMethodName, myMethodSignature.getName(debugProcess));
          }
          else {
            List list = referenceType.methodsByName(myMethodName);
            jdiMethod = (Method)(list.size() > 0 ? list.get(0) : null);
          }
          if (jdiMethod != null && jdiMethod.isStatic()) {
            return debugProcess.invokeMethod(context, (ClassType)referenceType, jdiMethod, args);
          }
        }
        throw EvaluateExceptionUtil.createEvaluateException(DebuggerBundle.message("evaluation.error.no.static.method", methodName));
      }
      // object should be an ObjectReference
      final ObjectReference objRef = (ObjectReference)object;
      ReferenceType _refType = referenceType;
      if (requiresSuperObject && (referenceType instanceof ClassType)) {
        _refType = ((ClassType)referenceType).superclass();
      }
      final Method jdiMethod = DebuggerUtilsEx.findMethod(_refType, myMethodName, signature);
      if (jdiMethod == null) {
        throw EvaluateExceptionUtil.createEvaluateException(DebuggerBundle.message("evaluation.error.no.instance.method", methodName));
      }
      if (requiresSuperObject) {
        return debugProcess.invokeInstanceMethod(context, objRef, jdiMethod, args, ObjectReference.INVOKE_NONVIRTUAL);
      }
      return debugProcess.invokeMethod(context, objRef, jdiMethod, args);
    }
    catch (Exception e) {
      if (LOG.isDebugEnabled()) {
        LOG.debug(e);
      }
      throw EvaluateExceptionUtil.createEvaluateException(e);
    }
  }
}
