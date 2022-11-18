// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.externalProcessAuthHelper

import com.intellij.ide.XmlRpcServer
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import externalApp.ExternalApp
import org.jetbrains.annotations.NonNls
import org.jetbrains.ide.BuiltInServerManager
import java.io.File
import java.io.IOException
import java.util.*

/**
 * The provider of external application scripts called by Git when a remote operation needs communication with the user.
 *
 * Usage:
 *  * Get the script from [getScriptPath].
 *  * Set up proper environment variable (e.g. `GIT_SSH` for SSH connections, or `GIT_ASKPASS` for HTTP) pointing to the script.
 *  * [Register][registerHandler] the handler of Git requests.
 *  * Call Git operation.
 *  * If the operation requires user interaction, the registered handler is called via XML RPC protocol.
 *    It can show a dialog in the GUI and return the answer via XML RPC to the external application, that further provides
 *    this value to the Git process.
 *  * [Unregister][unregisterHandler] the handler after operation has completed.
 *
 * @param handlerName Returns the name of the handler to be used by XML RPC client to call remote methods of a proper object.
 * @param aClass      Main class of the external application invoked by Git,
 * which is able to handle its requests and pass to the main IDEA instance.
 */
abstract class ExternalProcessHandlerService<T>(private val myScriptTempFilePrefix: @NonNls String,
                                                private val myHandlerName: @NonNls String,
                                                private val myScriptMainClass: Class<out ExternalApp?>) : Disposable {
  private val myScriptPaths: MutableMap<String, File> = HashMap()
  private val SCRIPT_FILE_LOCK = Any()

  private val handlers: MutableMap<UUID, T?> = HashMap()
  private val HANDLERS_LOCK = Any()

  /**
   * @return the port number for XML RCP
   */
  fun getXmlRcpPort(): Int {
    return BuiltInServerManager.getInstance().waitForStart().port;
  }

  /**
   * Get file to the script service
   *
   * @return path to the script
   * @throws IOException if script cannot be generated
   */
  @Throws(IOException::class)
  fun getScriptPath(scriptId: String, generator: ScriptGenerator, useBatchFile: Boolean): File {
    synchronized(SCRIPT_FILE_LOCK) {
      val id = scriptId + if (useBatchFile) "-bat" else "" //NON-NLS
      var scriptPath = myScriptPaths[id]
      if (scriptPath == null || !scriptPath.exists()) {
        val commandLine = generator.commandLine(myScriptMainClass, useBatchFile)
        scriptPath = ScriptGeneratorUtil.createTempScript(commandLine, "$myScriptTempFilePrefix-$scriptId", useBatchFile)
        myScriptPaths[id] = scriptPath
      }
      return scriptPath
    }
  }

  /**
   * Register handler. Note that handlers must be unregistered using [.unregisterHandler].
   *
   * @param handler a handler to register
   * @return an identifier to pass to the environment variable
   */
  fun registerHandler(handler: T): UUID {
    synchronized(HANDLERS_LOCK) {
      val xmlRpcServer = XmlRpcServer.getInstance()
      if (!xmlRpcServer.hasHandler(myHandlerName)) {
        xmlRpcServer.addHandler(myHandlerName, createRpcRequestHandlerDelegate())
      }
      val key = UUID.randomUUID()
      handlers[key] = handler
      return key
    }
  }

  override fun dispose() {
    val xmlRpcServer = ApplicationManager.getApplication().getServiceIfCreated(XmlRpcServer::class.java)
    xmlRpcServer?.removeHandler(myHandlerName)
  }

  /**
   * Creates an implementation of the xml rpc handler, which methods will be called from the external application.
   * This method should just delegate the call to the specific handler of type [T], which can be achieved by [.getHandler].
   *
   * @return New instance of the xml rpc handler delegate.
   */
  protected abstract fun createRpcRequestHandlerDelegate(): Any

  /**
   * Get handler for the key
   *
   * @param key the key to use
   * @return the registered handler
   */
  protected fun getHandler(key: UUID): T {
    synchronized(HANDLERS_LOCK) {
      return handlers[key] ?: throw IllegalStateException("No handler for the key $key")
    }
  }

  /**
   * Unregister handler by the key
   *
   * @param key the key to unregister
   */
  fun unregisterHandler(key: UUID) {
    synchronized(HANDLERS_LOCK) {
      if (handlers.remove(key) == null) {
        LOG.error("The handler $key is not registered")
      }
    }
  }

  companion object {
    private val LOG = Logger.getInstance(ExternalProcessHandlerService::class.java)
  }
}