// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.externalProcessAuthHelper

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.Computable
import com.intellij.openapi.util.Disposer
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.io.readUtf8
import externalApp.ExternalApp
import externalApp.ExternalAppHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.*
import org.jetbrains.annotations.NonNls
import org.jetbrains.ide.*
import org.jetbrains.io.response
import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

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

  private val handlers = ConcurrentHashMap<UUID, T>()

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
    val key: UUID = UUID.randomUUID()
    handlers[key] = handler

    Disposer.register(disposable) {
      unregisterHandler(key)
    }

    return key
  }

  private fun unregisterHandler(key: UUID) {
    val handler = handlers.remove(key)
    if (handler == null) {
      LOG.error("The handler $key is not registered")
    }
  }

  internal fun validateHandler(key: UUID): Boolean {
    return handlers.containsKey(key)
  }

  internal fun invokeHandler(key: UUID, requestBody: String): String? {
    val handler = handlers[key] ?: throw IllegalStateException("No handler for the key $key")
    return handleRequest(handler, requestBody)
  }

  protected abstract fun handleRequest(handler: T, requestBody: String): String?
}

abstract class ExternalProcessRest<T : ExternalAppHandler>(
  private val entryPointName: @NonNls String
) : RestService() {
  companion object {
    private val LOG = logger<ExternalProcessRest<*>>()
  }

  protected abstract val externalProcessHandler: ExternalProcessHandlerService<T>

  override fun getServiceName(): String = entryPointName

  override val reportErrorsAsPlainText: Boolean get() = true

  override fun isMethodSupported(method: HttpMethod): Boolean {
    return method === HttpMethod.POST
  }

  override fun getRequesterId(urlDecoder: QueryStringDecoder, request: FullHttpRequest, context: ChannelHandlerContext): Any {
    val uuid = getHandlerUUID(urlDecoder)
    if (uuid != null && externalProcessHandler.validateHandler(uuid)) {
      return uuid
    }
    return super.getRequesterId(urlDecoder, request, context)
  }

  override fun execute(urlDecoder: QueryStringDecoder, request: FullHttpRequest, context: ChannelHandlerContext): String? {
    val uuid = getHandlerUUID(urlDecoder) ?: return "Handler is not specified"
    val bodyContent = request.content().readUtf8()

    val channel = context.channel()

    val indicator = EmptyProgressIndicator()
    channel.closeFuture().addListener {
      indicator.cancel()
    }

    val executor = AppExecutorUtil.getAppExecutorService()
    CompletableFuture.supplyAsync({ runHandler(indicator, uuid, bodyContent) }, executor).handle { res, err ->
      if (err != null) {
        if (err is ProcessCanceledException) {
          channel.close()
        }
        else {
          LOG.warn(Throwable(err))
          sendStatus(HttpResponseStatus.INTERNAL_SERVER_ERROR, false, channel)
        }
      }
      else if (res != null) {
        sendResponse(request, context, response(res, StandardCharsets.UTF_8))
      }
      else {
        sendStatus(HttpResponseStatus.NO_CONTENT, false, channel)
      }
    }
    return null
  }

  private fun getHandlerUUID(urlDecoder: QueryStringDecoder): UUID? {
    val handlerId = urlDecoder.parameters()[ExternalAppHandler.HANDLER_ID_PARAMETER]?.singleOrNull() ?: return null
    return UUID.fromString(handlerId)
  }

  private fun runHandler(indicator: EmptyProgressIndicator, uuid: UUID, bodyContent: String): String? =
    ProgressManager.getInstance().runProcess(Computable { externalProcessHandler.invokeHandler(uuid, bodyContent) }, indicator)
}