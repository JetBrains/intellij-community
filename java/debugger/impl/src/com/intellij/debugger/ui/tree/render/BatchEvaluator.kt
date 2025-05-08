// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.ui.tree.render

import com.intellij.debugger.JavaDebuggerBundle
import com.intellij.debugger.engine.*
import com.intellij.debugger.engine.DebuggerManagerThreadImpl.Companion.assertIsManagerThread
import com.intellij.debugger.engine.evaluation.EvaluateException
import com.intellij.debugger.engine.evaluation.EvaluationContext
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.impl.DebuggerUtilsEx
import com.intellij.debugger.impl.DebuggerUtilsEx.mirrorOfArray
import com.intellij.debugger.impl.DebuggerUtilsImpl
import com.intellij.debugger.impl.MethodNotFoundException
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.IntelliJProjectUtil
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.getOrCreateUserData
import com.intellij.openapi.util.registry.Registry.Companion.`is`
import com.intellij.rt.debugger.BatchEvaluatorServer
import com.intellij.xdebugger.impl.ui.tree.nodes.XEvaluationOrigin
import com.intellij.xdebugger.impl.ui.tree.nodes.XEvaluationOrigin.Companion.computeWithOrigin
import com.sun.jdi.*
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.function.Consumer

class BatchEvaluator private constructor() {
  private val myBuffer = HashMap<SuspendContext?, MutableList<ToStringCommand>>()

  fun invoke(command: ToStringCommand) {
    assertIsManagerThread()

    val evaluationContext = command.evaluationContext
    val suspendContext = evaluationContext.getSuspendContext() as SuspendContextImpl
    if (command.value !is ObjectReference ||  // skip for primitive values
        (!`is`("debugger.batch.evaluation.force") && !`is`("debugger.batch.evaluation"))) {
      suspendContext.managerThread.invokeCommand(command)
    }
    else {
      myBuffer.computeIfAbsent(suspendContext) {
        suspendContext.managerThread.schedule(BatchEvaluatorCommand(evaluationContext))
        mutableListOf()
      }.add(command)
    }
  }

  private inner class BatchEvaluatorCommand(private val myEvaluationContext: EvaluationContext) : PossiblySyncCommand(
    myEvaluationContext.getSuspendContext() as SuspendContextImpl) {
    override fun syncAction(suspendContext: SuspendContextImpl) {
      val commands: MutableList<ToStringCommand> = myBuffer.remove(suspendContext)!!

      if ((commands.size == 1 && !`is`("debugger.batch.evaluation.force")) || !doEvaluateBatch(commands, myEvaluationContext)) {
        commands.forEach(Consumer { obj: ToStringCommand? -> obj!!.action() })
      }
    }

    override fun commandCancelled() {
      myBuffer.remove(suspendContext)
    }
  }

  companion object {
    private val LOG = Logger.getInstance(BatchEvaluator::class.java)

    private val BATCH_EVALUATOR_KEY = Key.create<BatchEvaluator>("BatchEvaluator")

    @JvmField
    val REMOTE_SESSION_KEY: Key<Boolean?> = Key<Boolean?>("is_remote_session_key")

    @JvmStatic
    fun getBatchEvaluator(evaluationContext: EvaluationContext): BatchEvaluator {
      val virtualMachineProxy = (evaluationContext as EvaluationContextImpl).virtualMachineProxy
      return virtualMachineProxy.getOrCreateUserData(BATCH_EVALUATOR_KEY) { BatchEvaluator() }
    }

    private fun doEvaluateBatch(requests: MutableList<ToStringCommand>, evaluationContext: EvaluationContext): Boolean {
      try {
        val values = requests.map { it.value }

        if (values.any { it !is ObjectReference }) {
          LOG.error("Batch toString evaluation can only be used for object references")
          return false
        }

        val evaluationContextImpl = evaluationContext as EvaluationContextImpl
        var value: String?
        if (values.size > 10) {
          value = invokeDefaultHelperMethod(values, evaluationContextImpl)
        }
        else {
          try {
            value = invokeHelperMethod("evaluate" + values.size, values, evaluationContextImpl)
          }
          catch (e: MethodNotFoundException) {
            LOG.warn("Unable to find helper method", e)
            value = invokeDefaultHelperMethod(values, evaluationContextImpl)
          }
        }

        if (value != null) {
          val bytes = value.toByteArray(StandardCharsets.ISO_8859_1)
          try {
            DataInputStream(ByteArrayInputStream(bytes)).use { dis ->
              var count = 0
              while (dis.available() > 0) {
                val error = dis.readBoolean()
                val message = dis.readUTF()
                if (count >= requests.size) {
                  LOG.error("Invalid number of results: required " + requests.size + ", reply = " + bytes.contentToString())
                  return false
                }
                val command = requests[count++]
                if (error) {
                  command.evaluationError(JavaDebuggerBundle.message("evaluation.error.method.exception", message))
                }
                else {
                  command.evaluationResult(message)
                }
              }
            }
          }
          catch (e: IOException) {
            LOG.error("Failed to read batch response", e, "reply was " + bytes.contentToString())
            return false
          }
          return true
        }
      }
      catch (e: ObjectCollectedException) {
        LOG.error(e)
      }
      catch (e: MethodNotFoundException) {
        if (IntelliJProjectUtil.isIntelliJPlatformProject(evaluationContext.getProject())) {
          var runProfileName: String? = null
          val debugProcess = evaluationContext.getDebugProcess() as DebugProcessImpl
          val session = debugProcess.session.getXDebugSession()
          if (session != null) {
            val runProfile = session.getRunProfile()
            if (runProfile != null) {
              runProfileName = runProfile.getName()
            }
          }
          if (runProfileName != null) {
            LOG.error("Unable to find helper method", e, "Run configuration: $runProfileName")
          }
        }
        else {
          LOG.error(e)
        }
      }
      catch (e: EvaluateException) {
        val exceptionFromTargetVM = e.exceptionFromTargetVM
        if (exceptionFromTargetVM != null && "java.io.UTFDataFormatException" == exceptionFromTargetVM.referenceType().name()) {
          // one of the strings is too long - just fall back to the regular separate toString calls
        }
        else {
          LOG.error(e)
        }
      }
      return false
    }

    private fun invokeDefaultHelperMethod(values: List<Value>, evaluationContextImpl: EvaluationContextImpl): String? {
      val objectArrayClass = evaluationContextImpl.debugProcess.findClass(
        evaluationContextImpl,
        "java.lang.Object[]",
        evaluationContextImpl.getClassLoader()) as ArrayType
      val argArray = mirrorOfArray(objectArrayClass, values, evaluationContextImpl)
      try {
        return invokeHelperMethod("evaluate", listOf(argArray), evaluationContextImpl)
      }
      finally {
        DebuggerUtilsEx.enableCollection(argArray)
      }
    }

    private fun invokeHelperMethod(helperMethodName: String, args: List<Value?>, evaluationContextImpl: EvaluationContextImpl): String? {
      return computeWithOrigin<String?>(evaluationContextImpl, XEvaluationOrigin.RENDERER) {
        DebuggerUtils.getInstance().processCollectibleValue<String?, Value?>(
          {
            DebuggerUtilsImpl.invokeHelperMethod(evaluationContextImpl, BatchEvaluatorServer::class.java, helperMethodName, args, false)
          },
          { (it as? StringReference)?.value() },
          evaluationContextImpl)
      }
    }
  }
}
