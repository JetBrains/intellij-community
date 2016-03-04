package org.jetbrains.builtInWebServer

import com.intellij.execution.ExecutionException
import com.intellij.execution.filters.TextConsoleBuilder
import com.intellij.execution.process.OSProcessHandler
import com.intellij.execution.process.ProcessAdapter
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.util.Consumer
import com.intellij.util.net.NetUtils
import org.jetbrains.concurrency.AsyncPromise
import org.jetbrains.concurrency.AsyncValueLoader
import org.jetbrains.concurrency.Promise
import org.jetbrains.concurrency.isRejected
import javax.swing.Icon

abstract class NetService @JvmOverloads protected constructor(protected val project: Project, private val consoleManager: ConsoleManager = ConsoleManager()) : Disposable {
  protected val processHandler: AsyncValueLoader<OSProcessHandler> = object : AsyncValueLoader<OSProcessHandler>() {
    override fun isCancelOnReject() = true

    private fun doGetProcessHandler(port: Int): OSProcessHandler? {
      try {
        return createProcessHandler(project, port)
      }
      catch (e: ExecutionException) {
        LOG.error(e)
        return null
      }
    }

    override fun load(promise: AsyncPromise<OSProcessHandler>): Promise<OSProcessHandler> {
      val port = NetUtils.findAvailableSocketPort()
      val processHandler = doGetProcessHandler(port)
      if (processHandler == null) {
        promise.setError("rejected")
        return promise
      }

      promise.rejected {
        processHandler.destroyProcess()
        Promise.logError(LOG, it)
      }

      val processListener = MyProcessAdapter()
      processHandler.addProcessListener(processListener)
      processHandler.startNotify()

      if (promise.isRejected) {
        return promise
      }

      ApplicationManager.getApplication().executeOnPooledThread {
        if (!promise.isRejected) {
          try {
            connectToProcess(promise, port, processHandler, processListener)
          }
          catch (e: Throwable) {
            if (!promise.setError(e)) {
              LOG.error(e)
            }
          }
        }
      }
      return promise
    }

    override fun disposeResult(processHandler: OSProcessHandler) {
      try {
        closeProcessConnections()
      }
      finally {
        processHandler.destroyProcess()
      }
    }
  }

  @Throws(ExecutionException::class)
  protected abstract fun createProcessHandler(project: Project, port: Int): OSProcessHandler?

  protected open fun connectToProcess(promise: AsyncPromise<OSProcessHandler>, port: Int, processHandler: OSProcessHandler, errorOutputConsumer: Consumer<String>) {
    promise.setResult(processHandler)
  }

  protected abstract fun closeProcessConnections()

  override fun dispose() {
    processHandler.reset()
  }

  protected open fun configureConsole(consoleBuilder: TextConsoleBuilder) {
  }

  protected abstract fun getConsoleToolWindowId(): String

  protected abstract fun getConsoleToolWindowIcon(): Icon

  open fun getConsoleToolWindowActions(): ActionGroup = DefaultActionGroup()

  private inner class MyProcessAdapter : ProcessAdapter(), Consumer<String> {
    override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
      print(event.text, ConsoleViewContentType.getConsoleViewType(outputType))
    }

    private fun print(text: String, contentType: ConsoleViewContentType) {
      consoleManager.getConsole(this@NetService).print(text, contentType)
    }

    override fun processTerminated(event: ProcessEvent) {
      processHandler.reset()
      print("${getConsoleToolWindowId()} terminated\n", ConsoleViewContentType.SYSTEM_OUTPUT)
    }

    override fun consume(message: String) {
      print(message, ConsoleViewContentType.ERROR_OUTPUT)
    }
  }
}