// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger

import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessListener
import com.intellij.util.ConcurrencyUtil

/**
 * It allows to avoid the hell with listeners by pretty linear program.
 * It allows to write:
 *
 * ```
 *  processBreakpoints {
 *    // code block 0
 *    waitAndResumeBreakpoint()
 *    // code block 1
 *    waitAndResumeBreakpoint()
 *    // code block 2
 *    waitAndResumeBreakpoint()
 *    // code block 3
 *    waitAndResumeBreakpoint()
 *    // code block 4
 *  }
 * ```
 *
 * instead of
 *
 * ```
 *  onBreakpoint { c1 ->
 *    onBreakpoint { c2 ->
 *      onBreakpoint { c3 ->
 *        onBreakpoint { c4 ->
 *          resume(c4)
 *          // some hacks with threading {
 *          //   code block 4
 *          // }
 *        }
 *        resume(c3)
 *        // some hacks with threading {
 *        //   code block 3
 *        // }
 *      }
 *      resume(c2)
 *      // some hacks with threading {
 *      //   code block 2
 *      // }
 *    }
 *    resume(c1)
 *    // some hacks with threading {
 *    //   code block 1
 *    // }
 *  }
 *  // some hacks with threading {
 *  //   code block 0
 *  // }
 * ```
 */
fun ExecutionWithDebuggerToolsTestCase.processBreakpoints(test: suspend SequenceScope<Nothing?>.() -> Unit) {
  val executor = ConcurrencyUtil.newSingleThreadExecutor("DebuggerTestExecutor")

  debugProcess.addProcessListener(object : ProcessListener {
    override fun processTerminated(event: ProcessEvent) {
      executor.shutdown()
    }
  })

  // Make test as sequence of code blocks that separated by waitAndResumeBreakpoint call
  val iterator = iterator(test)
  // Register listener that will called when every breakpoint is reached in target program
  onEveryBreakpoint { context ->
    // Resume breakpoint in target program
    resume(context)
    // Current thread is DebuggerManagerThread we cannot block it to make test,
    // because program won't be resumed,
    // because resume call schedules resume task on DebuggerManagerThread
    executor.execute {
      // Advance iterator of test blocks
      iterator.next()
      // Advance test to next breakpoint
      // It returns false if breakpoints don't reached
      logException { iterator.hasNext() }
    }
  }
  // It is necessary to execute all test blocks on single thread
  executor.execute {
    // Advance test to first breakpoint
    // It returns false if breakpoints don't reached
    logException { iterator.hasNext() }
  }
}

private fun <R> logException(action: () -> R): R {
  return try {
    action()
  }
  catch (ex: Throwable) {
    ex.printStackTrace()
    throw ex
  }
}

/**
 * It makes using coroutines pretty magical
 */
suspend fun SequenceScope<Nothing?>.waitAndResumeBreakpoint() {
  yield(null)
}