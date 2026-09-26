package org.jetbrains.intellij.build.io

import com.intellij.platform.buildScripts.concurrency.TaskFailedException
import com.intellij.platform.buildScripts.concurrency.taskScope
import org.jetbrains.annotations.ApiStatus
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.concurrent.Executors
import kotlin.time.Duration
import kotlin.time.toJavaDuration

/** Owns the client and its executor until [action] returns. */
@ApiStatus.Internal
fun <T> withHttpClient(
  connectTimeout: Duration,
  version: HttpClient.Version = HttpClient.Version.HTTP_2,
  action: (HttpClient) -> T,
): T {
  return OwnedHttpClient(connectTimeout, version, HttpClient.Redirect.ALWAYS).use { owner ->
    try {
      action(owner.client)
    }
    catch (failure: Throwable) {
      owner.client.shutdownNow()
      throw failure
    }
  }
}

internal class OwnedHttpClient(connectTimeout: Duration, version: HttpClient.Version, redirects: HttpClient.Redirect) : AutoCloseable {
  private val executor = Executors.newVirtualThreadPerTaskExecutor()
  val client: HttpClient = try {
    HttpClient.newBuilder()
      .executor(executor)
      .connectTimeout(connectTimeout.toJavaDuration())
      .followRedirects(redirects)
      .version(version)
      .build()
  }
  catch (failure: Throwable) {
    executor.close()
    throw failure
  }

  override fun close() {
    executor.use { client.close() }
  }
}

/** Joins the request task and consumes the response body within [timeout]. */
@ApiStatus.Internal
fun sendHttpRequest(client: HttpClient, request: HttpRequest, timeout: Duration): HttpResponse<String> {
  val identityRequest = HttpRequest.newBuilder(request) { _, _ -> true }
    .setHeader("Accept-Encoding", "identity")
    .build()
  return runHttpTask(timeout) { client.send(identityRequest, HttpResponse.BodyHandlers.ofString()) }
}

internal fun <T> runHttpTask(timeout: Duration? = null, action: () -> T): T {
  if (Thread.currentThread().isInterrupted) throw InterruptedException("The HTTP request was interrupted")
  try {
    return taskScope(name = "http request", timeout = timeout) {
      val response = fork("http request") { action() }
      join()
      response.get()
    }
  }
  catch (failure: TaskFailedException) {
    val cause = failure.cause
    if (cause == null || cause is InterruptedException) throw failure
    throw cause
  }
}
