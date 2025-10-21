// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.externalProcessAuthHelper

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.openapi.util.Computable
import com.intellij.openapi.util.Disposer
import com.intellij.platform.eel.EelApi
import com.intellij.platform.eel.EelExecApi
import com.intellij.platform.eel.provider.asNioPath
import com.intellij.platform.eel.provider.utils.asOutputStream
import com.intellij.util.cancelOnDispose
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.io.readUtf8
import externalApp.ExternalApp
import externalApp.ExternalAppEntry
import externalApp.ExternalAppHandler
import externalApp.ExternalCli
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.FullHttpRequest
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.handler.codec.http.QueryStringDecoder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls
import org.jetbrains.ide.BuiltInServerManager
import org.jetbrains.ide.RestService
import org.jetbrains.io.response
import java.io.File
import java.io.IOException
import java.io.PrintStream
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.pathString

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
  private val scriptMainClass: Class<out ExternalApp>,
  private val scriptBody: ExternalCli?,
  private val requiredEnvVariables: List<String>,
  private val coroutineScope: CoroutineScope?
) {

  @Deprecated("Use constructor with scriptBody")
  constructor(scriptNamePrefix: String, scriptMainClass: Class<out ExternalApp>) :
    this(scriptNamePrefix, scriptMainClass, null, emptyList(), null)

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

  /**
   * Get file to the script service
   *
   * @return path to the script
   */
  @Throws(IOException::class)
  @ApiStatus.Internal
  fun getCallbackScriptPath(eelApi: EelApi, useBatchFile: Boolean, disposable: Disposable): Path {
    if (scriptBody == null || coroutineScope == null) throw UnsupportedOperationException("Handler ${this.javaClass} doesn't support eel external cli.")
    return runBlockingMaybeCancellable {
      val script = eelApi.exec.createExternalCli(object : EelExecApi.LocalExternalCliOptions {
        override val mainClass = scriptMainClass
        override val useBatchFile = useBatchFile
        override val filePrefix = scriptNamePrefix
        override val envVariablesToCapture = requiredEnvVariables
      })
      coroutineScope.launch {
        script.consumeInvocations { process ->
          process.toExternalAppEntry().use { externalAppEntry ->
            scriptBody.entryPoint(externalAppEntry)
          }
        }
      }.cancelOnDispose(disposable)
      script.path.asNioPath()
    }
  }

  private class EelExternalAppEntry(val process: EelExecApi.ExternalCliProcess) : ExternalAppEntry, AutoCloseable {
    val myStderr: PrintStream = process.stderr.asOutputStream().let(::PrintStream)
    val myStdout: PrintStream = process.stdout.asOutputStream().let(::PrintStream)
    override fun getArgs(): Array<out String> = process.args.toTypedArray()
    override fun getEnvironment(): Map<String, String> = process.environment
    override fun getWorkingDirectory(): String = process.workingDir.asNioPath().pathString
    override fun getStderr(): PrintStream = myStderr
    override fun getStdout(): PrintStream = myStdout

    override fun close() {
      stderr.close()
    }
  }

  private fun EelExecApi.ExternalCliProcess.toExternalAppEntry() = EelExternalAppEntry(this)

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
      LOG_SERVICE.error("The handler $key is not registered")
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
          LOG_REST.warn(Throwable(err))
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

private val LOG_SERVICE = logger<ExternalProcessHandlerService<*>>()

private val LOG_REST = logger<ExternalProcessRest<*>>()
