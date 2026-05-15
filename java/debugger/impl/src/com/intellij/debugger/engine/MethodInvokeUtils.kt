// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.engine

import com.intellij.debugger.engine.DebuggerUtils.isPrimitiveType
import com.intellij.debugger.engine.MethodInvokeUtils.getHelperExceptionStackTrace
import com.intellij.debugger.engine.evaluation.EvaluateException
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.engine.evaluation.expression.BoxingEvaluator
import com.intellij.debugger.engine.evaluation.expression.UnBoxingEvaluator
import com.intellij.debugger.impl.DebuggerUtilsAsync
import com.intellij.debugger.impl.DebuggerUtilsEx
import com.intellij.debugger.impl.DebuggerUtilsEx.isVoid
import com.intellij.debugger.impl.DebuggerUtilsImpl
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Attachment
import com.intellij.openapi.diagnostic.RuntimeExceptionWithAttachments
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.registry.RegistryManager
import com.intellij.psi.CommonClassNames
import com.intellij.rt.debugger.JvmThreadHelper
import com.intellij.rt.debugger.MethodInvoker
import com.intellij.util.BitUtil.isSet
import com.intellij.xdebugger.impl.evaluate.XEvaluationOrigin
import com.jetbrains.jdi.ArrayReferenceImpl
import com.sun.jdi.ArrayReference
import com.sun.jdi.ArrayType
import com.sun.jdi.IncompatibleThreadStateException
import com.sun.jdi.IntegerValue
import com.sun.jdi.Method
import com.sun.jdi.ObjectReference
import com.sun.jdi.ObjectReference.INVOKE_NONVIRTUAL
import com.sun.jdi.ReferenceType
import com.sun.jdi.ThreadReference
import com.sun.jdi.Value
import org.jetbrains.annotations.ApiStatus
import java.util.EnumSet
import java.util.concurrent.TimeUnit

@ApiStatus.Internal
object MethodInvokeUtils {
  @JvmStatic
  internal val INVOKE_WITH_HELPER_KEY: Key<Boolean> = Key<Boolean>("invoke.with.helper")

  enum class MethodStackScanStatus {
    FOUND,
    NOT_FOUND,
    INCOMPLETE_STACKS,
    TOO_MANY_INCOMPLETE_STACKS,
    TIMED_OUT,
  }

  data class MethodStackScanResult(
    val status: MethodStackScanStatus,
    val threadsToCheck: List<ThreadReference>,
    val elapsedMillis: Long,
  )

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

  fun getMethodHandlesImplLookup(evaluationContext: EvaluationContextImpl): ObjectReference? {
    val theClass = evaluationContext.debugProcess.findLoadedClass(
      evaluationContext.suspendContext,
      "java.lang.invoke.MethodHandles\$Lookup", null
    ) ?: run {
      logger<MethodInvokeUtils>().error("Failed to find MethodHandles\$Lookup class, java version: " + evaluationContext.virtualMachineProxy.version())
      return null
    }
    val theField = DebuggerUtils.findField(theClass, "IMPL_LOOKUP") ?: run {
      logger<MethodInvokeUtils>().error("Failed to find MethodHandles.Lookup.IMPL_LOOKUP field, java version: " + evaluationContext.virtualMachineProxy.version())
      return null
    }
    return theClass.getValue(theField) as? ObjectReference ?: run {
      logger<MethodInvokeUtils>().error("Failed to get MethodHandles.Lookup.IMPL_LOOKUP field value, java version: " + evaluationContext.virtualMachineProxy.version())
      return null
    }
  }

  fun findAnyOfSpecifiedMethodsInAnyThreadStack(
    evaluationContext: EvaluationContextImpl,
    methods: Collection<Method>,
    maxStackTraceFramesToScan: Int = JvmThreadHelper.METHOD_STACK_SCAN_UNLIMITED_DEPTH,
    threadToSkip: ThreadReference? = null,
    timeoutMillis: Int = methodStackScanDefaultTimeoutMillis,
  ): MethodStackScanResult {
    val methodKeys = methods.asSequence()
      .map { it.declaringType().name() to it.name() }
      .distinct()
      .flatMap { sequenceOf(it.first, it.second) }
      .map { DebuggerUtilsEx.mirrorOfString(it, evaluationContext) }
      .toList()
    if (methodKeys.isEmpty()) {
      return MethodStackScanResult(MethodStackScanStatus.NOT_FOUND, emptyList(), 0)
    }

    val debugProcess = evaluationContext.debugProcess
    val stringArrayClass = debugProcess.findClass(
      evaluationContext,
      CommonClassNames.JAVA_LANG_STRING + "[]",
      evaluationContext.getClassLoader(),
    ) as ArrayType
    val targetMethods = DebuggerUtilsEx.mirrorOfArray(stringArrayClass, methodKeys, evaluationContext)
    val lookup = if (hasVirtualThreadSupport(evaluationContext)) {
      getMethodHandlesImplLookup(evaluationContext) ?: throw EvaluateException("Cannot get MethodHandles.Lookup.IMPL_LOOKUP")
    }
    else {
      null
    }
    val startedAtNanos = System.nanoTime()
    val result = DebuggerUtilsImpl.invokeHelperMethod(
      evaluationContext,
      JvmThreadHelper::class.java,
      "findSpecifiedMethodsInAnyThreadStack",
      listOf(
        lookup,
        targetMethods,
        evaluationContext.virtualMachineProxy.mirrorOf(maxStackTraceFramesToScan),
        threadToSkip,
        evaluationContext.virtualMachineProxy.mirrorOf(timeoutMillis),
      ),
      false,
      JvmThreadHelper::class.java.name + '$' + "MethodTarget",
      JvmThreadHelper::class.java.name + '$' + "MethodStackScanResult",
    )
    val elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAtNanos)
    val values = (result as? ArrayReference)?.values ?: throw EvaluateException("Unexpected method stack check helper result: $result")
    if (values.isEmpty()) {
      throw EvaluateException("Method stack check helper returned an empty result")
    }
    val statusValue = readMethodStackScanStatus(evaluationContext, values[0])
    val threadsToCheck = values.asSequence().drop(1).map {
      it as? ThreadReference ?: throw EvaluateException("Unexpected thread to recheck from method stack check helper: $it")
    }.toList()
    return MethodStackScanResult(methodStackScanStatus(statusValue), threadsToCheck, elapsedMillis)
  }

  private fun hasVirtualThreadSupport(evaluationContext: EvaluationContextImpl): Boolean {
    return evaluationContext.virtualMachineProxy.classesByName("java.lang.VirtualThread").isNotEmpty()
  }

  private fun readMethodStackScanStatus(evaluationContext: EvaluationContextImpl, value: Value?): Int {
    val unboxedValue = UnBoxingEvaluator.unbox(value, evaluationContext)
    if (unboxedValue is IntegerValue) return unboxedValue.intValue()
    throw EvaluateException("Unexpected method stack check helper status: $value")
  }

  private fun methodStackScanStatus(status: Int): MethodStackScanStatus {
    return when (status) {
      JvmThreadHelper.METHOD_STACK_STATUS_FOUND -> MethodStackScanStatus.FOUND
      JvmThreadHelper.METHOD_STACK_STATUS_NOT_FOUND -> MethodStackScanStatus.NOT_FOUND
      JvmThreadHelper.METHOD_STACK_STATUS_INCOMPLETE_STACKS -> MethodStackScanStatus.INCOMPLETE_STACKS
      JvmThreadHelper.METHOD_STACK_STATUS_TOO_MANY_INCOMPLETE_STACKS -> MethodStackScanStatus.TOO_MANY_INCOMPLETE_STACKS
      JvmThreadHelper.METHOD_STACK_STATUS_TIMED_OUT -> MethodStackScanStatus.TIMED_OUT
      else -> throw EvaluateException("Unexpected method stack check helper status: $status")
    }
  }
}

private val ORIGINS_FOR_USE_WITH_HELPER = EnumSet.of(XEvaluationOrigin.DIALOG,
                                                     XEvaluationOrigin.INLINE,
                                                     XEvaluationOrigin.EDITOR,
                                                     XEvaluationOrigin.BREAKPOINT_LOG)

private fun EvaluationContextImpl.shouldUseHelper(): Boolean {
  return when (Registry.get("debugger.evaluate.method.helper").selectedOption) {
    "off" -> false
    "always" -> true
    else -> ORIGINS_FOR_USE_WITH_HELPER.contains(XEvaluationOrigin.getOrigin(this)) || getUserData(MethodInvokeUtils.INVOKE_WITH_HELPER_KEY) == true
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
      !evaluationContext.shouldUseHelper() ||
      isSet(invocationOptions, INVOKE_NONVIRTUAL) || //TODO: support
      isPrimitiveType(method.returnTypeName()) ||
      (isVoid(method) && !method.isConstructor) ||
      "clone" == method.name()) {
    return INVOCATION_FAILED
  }

  val methodDeclaringType = method.declaringType()
  require(DebuggerUtilsImpl.instanceOf(type, methodDeclaringType)) { "Invalid method" }
  if (objRef != null) {
    require(DebuggerUtilsImpl.instanceOf(objRef.referenceType(), methodDeclaringType)) { "Invalid method" }
  }

  // Class.forName may check getCallerClass which is different if helper is used
  if (method.name().equals("forName") && methodDeclaringType.name() == CommonClassNames.JAVA_LANG_CLASS) {
    return INVOCATION_FAILED
  }

  val debugProcess = evaluationContext.debugProcess
  val invokerArgs = mutableListOf<Value?>()

  val implLookup = MethodInvokeUtils.getMethodHandlesImplLookup(evaluationContext)
  if (implLookup == null) {
    return INVOCATION_FAILED
  }

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
    var value = DebuggerUtilsImpl.invokeHelperMethod(evaluationContext, MethodInvoker::class.java, helperMethodName, invokerArgs, false)
    if (value is ArrayReference) { // wrapped
      val wrapper = value
      value = value.getValue(0)
      if (value is ObjectReference) {
        evaluationContext.suspendContext.keepAsync(value)
        // clear the reference
        if (DebuggerUtilsAsync.isAsyncEnabled() && wrapper is ArrayReferenceImpl) {
          wrapper.setFirstElementToNull()
        }
        else {
          wrapper.setValue(0, null)
        }
      }
    }
    return InvocationResult(true, value)
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
    return INVOCATION_FAILED
  }
}

private val HELPER_FRAMES = setOf(MethodInvoker::class.qualifiedName + ".invoke", "java.lang.invoke.MethodHandle.invoke")
private fun isHelperFrame(frame: String): Boolean = HELPER_FRAMES.any { frame.contains(it) }

internal data class InvocationResult(@get:JvmName("isSuccess") val success: Boolean, val value: Value?)

private val INVOCATION_FAILED = InvocationResult(false, null)

private val methodStackScanDefaultTimeoutMillis: Int
  get() = RegistryManager.getInstance().intValue("debugger.helper.method.stack.scan.timeout.ms", 200)
