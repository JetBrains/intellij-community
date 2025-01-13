// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import com.intellij.psi.CommonClassNames
import com.intellij.rt.debugger.MethodInvoker
import com.intellij.util.BitUtil.isSet
import com.sun.jdi.*
import com.sun.jdi.ObjectReference.INVOKE_NONVIRTUAL
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object MethodInvokeUtils {
  fun getHelperExceptionStackTrace(evaluationContext: EvaluationContextImpl, e: Exception): String? {
    if (e !is EvaluateException) return null
    val exceptionFromTargetVM = e.exceptionFromTargetVM ?: return null
    val values = DebuggerUtilsImpl.invokeThrowableGetStackTrace(exceptionFromTargetVM, evaluationContext, true)?.values
    if (values.isNullOrEmpty()) return null
    // drop user frames
    val currentStackDepth = DebugProcessImpl.getEvaluationThread(evaluationContext).frameCount()
    val keepLines = if (values.size <= currentStackDepth) values.size else values.size - currentStackDepth
    val stackTraceString = getExceptionTextFromStackTraceValues(evaluationContext, exceptionFromTargetVM, values, keepLines)
    if (values.size <= currentStackDepth) {
      logger<MethodInvokeUtils>().error("Invalid helper stack (expected currentStackDepth = ${currentStackDepth}) : ${stackTraceString}")
    }
    return stackTraceString
  }

  /**
   * Slow implementation and does not include async stack trace, but does not use helpers
   */
  @JvmStatic
  fun getExceptionTextViaArray(evaluationContext: EvaluationContextImpl, exceptionObject: ObjectReference): String? {
    val values = DebuggerUtilsImpl.invokeThrowableGetStackTrace(exceptionObject, evaluationContext, true)?.values
    if (values.isNullOrEmpty()) return null
    return getExceptionTextFromStackTraceValues(evaluationContext, exceptionObject, values)
  }

  private fun getExceptionTextFromStackTraceValues(
    evaluationContext: EvaluationContextImpl,
    exceptionObject: ObjectReference,
    stackTraceValues: List<Value>,
    keepLines: Int = Int.MAX_VALUE,
  ): String? {
    return DebuggerUtils.getValueAsString(evaluationContext, exceptionObject) + "\n" +
           stackTraceValues.asSequence()
             .take(keepLines)
             .map { "\tat ${DebuggerUtils.getValueAsString(evaluationContext, it)}" }
             .joinToString(separator = "\n", postfix = "\n")
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

  val methodDeclaringType = method.declaringType()
  require(DebuggerUtilsImpl.instanceOf(type, methodDeclaringType)) { "Invalid method" }
  if (objRef != null) {
    require(DebuggerUtilsImpl.instanceOf(objRef.referenceType(), methodDeclaringType)) { "Invalid method" }
  }

  val debugProcess = evaluationContext.debugProcess
  val invokerArgs = mutableListOf<Value?>()

  val lookupClass =
    debugProcess.findClass(evaluationContext, "java.lang.invoke.MethodHandles\$Lookup", evaluationContext.getClassLoader())
  if (lookupClass == null) {
    logger<MethodInvokeUtils>().error("Lookup class not found, java version " + evaluationContext.virtualMachineProxy.version())
    return InvocationResult(false, null)
  }
  val implLookup = lookupClass.getValue(DebuggerUtils.findField(lookupClass, "IMPL_LOOKUP")) as ObjectReference

  invokerArgs.add(implLookup) // lookup
  invokerArgs.add(type.classObject()) // class
  invokerArgs.add(objRef) // object
  invokerArgs.add(DebuggerUtilsEx.mirrorOfString(method.name() + ";" + method.signature(), evaluationContext)) // method name and descriptor
  invokerArgs.add(methodDeclaringType.classLoader()) // method's declaring type class loader to be able to resolve parameter types

  // argument values
  val boxedArgs = originalArgs.map { BoxingEvaluator.box(it, evaluationContext) as Value? }

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
    if (e is EvaluateException && e.cause is IncompatibleThreadStateException) {
      throw e
    }
    if (ApplicationManager.getApplication().isInternal) {
      val attachments = listOfNotNull(helperExceptionStackTrace?.let { Attachment("helper_stack.txt", it).apply { isIncluded = true } }).toTypedArray()
      DebuggerUtilsImpl.logError("Exception from helper (while evaluating ${methodDeclaringType.name() + "." + method.name()}): ${e.message}",
                                 RuntimeExceptionWithAttachments(e, *attachments)) // log helper exception if available
    }
    return InvocationResult(false, null)
  }
}

private val HELPER_FRAMES = setOf(MethodInvoker::class.qualifiedName + ".invoke", "java.lang.invoke.MethodHandle.invoke")
private fun isHelperFrame(frame: String): Boolean = HELPER_FRAMES.any { frame.contains(it) }

internal data class InvocationResult(@get:JvmName("isSuccess") val success: Boolean, val value: Value?)

