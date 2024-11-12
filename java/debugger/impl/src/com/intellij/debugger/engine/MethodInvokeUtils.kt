// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.engine

import com.intellij.debugger.engine.DebuggerUtils.isPrimitiveType
import com.intellij.debugger.engine.MethodInvokeUtils.getHelperExceptionStackTrace
import com.intellij.debugger.engine.evaluation.EvaluateException
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.engine.evaluation.expression.BoxingEvaluator
import com.intellij.debugger.impl.DebuggerUtilsEx
import com.intellij.debugger.impl.DebuggerUtilsEx.isVoid
import com.intellij.debugger.impl.DebuggerUtilsImpl
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Attachment
import com.intellij.openapi.diagnostic.RuntimeExceptionWithAttachments
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.CommonClassNames
import com.intellij.rt.debugger.MethodInvoker
import com.intellij.util.BitUtil.isSet
import com.sun.jdi.*
import com.sun.jdi.ObjectReference.INVOKE_NONVIRTUAL

object MethodInvokeUtils {
  fun getHelperExceptionStackTrace(evaluationContext: EvaluationContextImpl, e: Exception): String? {
    e as? EvaluateException ?: return null
    val exceptionFromTargetVM = e.exceptionFromTargetVM ?: return null
    var exceptionStack = DebuggerUtilsImpl.getExceptionText(evaluationContext, exceptionFromTargetVM)
    if (!exceptionStack.isNullOrEmpty()) {
      // drop user frames
      val currentStackDepth = DebugProcessImpl.getEvaluationThread(evaluationContext).frameCount()
      val lines = StringUtil.splitByLines(exceptionStack) // exclude empty lines
      if (lines.size > currentStackDepth) {
        return lines.asSequence().take(lines.size - currentStackDepth).joinToString(separator = "\n")
      }
      else {
        logger<MethodInvokeUtils>().error("Invalid helper stack (expected currentStackDepth = ${currentStackDepth}) : ${exceptionStack}")
        return exceptionStack
      }
    }
    return null
  }
}

@Throws(EvaluateException::class)
internal fun tryInvokeWithHelper(
  type: ReferenceType,
  objRef: ObjectReference?,
  method: Method,
  originalArgs: List<Value?>,
  evaluationContext: EvaluationContextImpl,
  invocationOptions: Int,
  internalEvaluate: Boolean,
): InvocationResult {
  if (internalEvaluate ||
      !Registry.`is`("debugger.evaluate.method.helper") ||
      isSet(invocationOptions, INVOKE_NONVIRTUAL) || //TODO: support
      isPrimitiveType(method.returnTypeName()) ||
      (isVoid(method) && !method.isConstructor) ||
      "clone" == method.name()) {
    return InvocationResult(false, null)
  }

  val debugProcess = evaluationContext.debugProcess
  val invokerArgs = mutableListOf<Value?>()

  val lookupClass =
    debugProcess.findClass(evaluationContext, "java.lang.invoke.MethodHandles\$Lookup", evaluationContext.getClassLoader())
  val implLookup = lookupClass.getValue(DebuggerUtils.findField(lookupClass, "IMPL_LOOKUP")) as ObjectReference

  invokerArgs.add(implLookup) // lookup
  invokerArgs.add(type.classObject()) // class
  invokerArgs.add(objRef) // object
  invokerArgs.add(DebuggerUtilsEx.mirrorOfString(method.name() + ";" + method.signature(), evaluationContext)) // method name and descriptor
  invokerArgs.add(method.declaringType().classLoader()) // method's declaring type class loader to be able to resolve parameter types

  // argument values
  val args = originalArgs.toMutableList()
  if (method.isVarArgs) {
    // If vararg is of a primitive type and an array is passed, we need to unwrap it (and box later)
    (args.lastOrNull() as? ArrayReference)?.let {
      val argumentTypeNames = method.argumentTypeNames()
      if (args.size == argumentTypeNames.size && isPrimitiveType(argumentTypeNames.last().removeSuffix("[]"))) {
        args.removeLast()
        args.addAll(it.values)
      }
    }
  }

  val boxedArgs = args.map { BoxingEvaluator.box(it, evaluationContext) as Value? }

  var helperMethodName = "invoke"
  if (boxedArgs.size > 10) {
    val objectArrayClass = debugProcess.findClass(
      evaluationContext,
      CommonClassNames.JAVA_LANG_OBJECT + "[]",
      evaluationContext.getClassLoader()) as ArrayType

    invokerArgs.add(DebuggerUtilsEx.mirrorOfArray(objectArrayClass, boxedArgs, evaluationContext)) // args as array
  }
  else {
    helperMethodName = "invoke${boxedArgs.size}"
    invokerArgs.addAll(boxedArgs) // add all args directly to the helper args
  }

  try {
    return InvocationResult(true, DebuggerUtilsImpl.invokeHelperMethod(evaluationContext, MethodInvoker::class.java, helperMethodName, invokerArgs, false))
  }
  catch (e: Exception) {
    val helperExceptionStackTrace = getHelperExceptionStackTrace(evaluationContext, e)
    val methodCallString = method.name() + "("
    if (helperExceptionStackTrace?.lineSequence()?.filterNot(::isHelperFrame)?.any { it.contains(methodCallString) } == true) {
      throw e
    }
    if (ApplicationManager.getApplication().isInternal) {
      val attachments = listOfNotNull(helperExceptionStackTrace?.let { Attachment("helper_stack.txt", it).apply { isIncluded = true } }).toTypedArray()
      DebuggerUtilsImpl.logError("Exception from helper (while evaluating ${method.declaringType().name() + "." + method.name()}): ${e.message}",
                                 RuntimeExceptionWithAttachments(e, *attachments)) // log helper exception if available
    }
    return InvocationResult(false, null)
  }
}

private val HELPER_FRAMES = setOf(MethodInvoker::class.qualifiedName + ".invoke", "java.lang.invoke.MethodHandle.invoke")
private fun isHelperFrame(frame: String): Boolean = HELPER_FRAMES.any { frame.contains(it) }

internal data class InvocationResult(@get:JvmName("isSuccess") val success: Boolean, val value: Value?)

