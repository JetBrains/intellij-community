// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.externalProcessAuthHelper

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.Disposer
import com.intellij.util.io.readUtf8
import externalApp.ExternalApp
import externalApp.ExternalAppHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.FullHttpRequest
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.QueryStringDecoder
import org.jetbrains.annotations.NonNls
import org.jetbrains.ide.BuiltInServerManager
import org.jetbrains.ide.RestService
import org.jetbrains.io.response
import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.*

/**
 * The provider of external application scripts called by Git when a remote operation needs communication with the user.
 *
 * Usage:
 * - Register corresponding [ExternalProcessHandlerService] service and [ExternalProcessRest] extension in plugin.xml.
 * - Get the script path from [getCallbackScriptPath] and IDE port from [getIdePort].
 * - Register handler using [registerHandler].
 * - Call external application, passing ENV from [ExternalAppHandler] pointing to the script and IDE port.
 * - If the operation requests user interaction, the registered handler is called via [ExternalApp] and IDE Rest protocol.
 * - Do the task (ex: show GUI dialog) and return the answer to the external application via [ExternalApp].
 */
abstract class ExternalProcessHandlerService<T : ExternalAppHandler>(
  private val scriptNamePrefix: @NonNls String,
  private val scriptMainClass: Class<out ExternalApp>
) {
  companion object {
    private val LOG = logger<ExternalProcessHandlerService<*>>()
  }

  private val scriptPaths = HashMap<@NonNls String, File>()
  private val SCRIPT_FILE_LOCK = Any()

  private val handlers = HashMap<UUID, T>()
  private val HANDLERS_LOCK = Any()

  fun getIdePort(): Int {
    return BuiltInServerManager.getInstance().waitForStart().port
  }

  /**
   * Get file to the script service
   *
   * @return path to the script
   */
  @Throws(IOException::class)
  fun getCallbackScriptPath(scriptId: String, generator: ScriptGenerator, useBatchFile: Boolean): File {
    val id = scriptId + if (useBatchFile) "-bat" else "" //NON-NLS

    synchronized(SCRIPT_FILE_LOCK) {
      val oldFile = scriptPaths[id]
      if (oldFile != null && oldFile.exists()) return oldFile

      val commandLine = generator.commandLine(scriptMainClass, useBatchFile)
      val newFile = ScriptGeneratorUtil.createTempScript(commandLine, "$scriptNamePrefix-$scriptId", useBatchFile)
      scriptPaths[id] = newFile
      return newFile
    }
  }

  fun registerHandler(handler: T, disposable: Disposable): UUID {
    val key: UUID
    synchronized(HANDLERS_LOCK) {
      key = UUID.randomUUID()
      handlers[key] = handler
    }

    Disposer.register(disposable) {
      unregisterHandler(key)
    }

    return key
  }

  private fun unregisterHandler(key: UUID) {
    synchronized(HANDLERS_LOCK) {
      if (handlers.remove(key) == null) {
        LOG.error("The handler $key is not registered")
      }
    }
  }

  private fun getHandler(key: UUID): T {
    synchronized(HANDLERS_LOCK) {
      return handlers[key] ?: throw IllegalStateException("No handler for the key $key")
    }
  }

  internal fun invokeHandler(key: UUID, requestBody: String): String? {
    return handleRequest(getHandler(key), requestBody)
  }

  protected abstract fun handleRequest(handler: T, requestBody: String): String?
}

abstract class ExternalProcessRest<T : ExternalAppHandler>(
  private val entryPointName: @NonNls String
) : RestService() {
  protected abstract val externalProcessHandler: ExternalProcessHandlerService<T>

  override fun getServiceName(): String = entryPointName

  override val reportErrorsAsPlainText: Boolean get() = true

  override fun isMethodSupported(method: HttpMethod): Boolean {
    return method === HttpMethod.POST
  }

  override fun execute(urlDecoder: QueryStringDecoder, request: FullHttpRequest, context: ChannelHandlerContext): String? {
    val handlerId = urlDecoder.parameters()[ExternalAppHandler.HANDLER_ID_PARAMETER]?.singleOrNull() ?: return "Handler is not specified"
    val uuid = UUID.fromString(handlerId)
    val bodyContent = request.content().readUtf8()

    val result = externalProcessHandler.invokeHandler(uuid, bodyContent)

    if (result != null) {
      sendResponse(request, context, response(result, StandardCharsets.UTF_8))
    }
    else {
      sendOk(request, context)
    }
    return null
  }
}