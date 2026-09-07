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
  return Executors.newVirtualThreadPerTaskExecutor().use { executor ->
    HttpClient.newBuilder()
      .executor(executor)
      .connectTimeout(connectTimeout.toJavaDuration())
      .followRedirects(HttpClient.Redirect.ALWAYS)
      .version(version)
      .build().use { client ->
        try {
          action(client)
        }
        catch (failure: Throwable) {
          client.shutdownNow()
          throw failure
        }
      }
  }
}

/** Joins the request task and consumes the response body within [timeout]. */
@ApiStatus.Internal
fun sendHttpRequest(client: HttpClient, request: HttpRequest, timeout: Duration): HttpResponse<String> {
  if (Thread.currentThread().isInterrupted) throw InterruptedException("The HTTP request was interrupted")
  val identityRequest = HttpRequest.newBuilder(request) { _, _ -> true }
    .setHeader("Accept-Encoding", "identity")
    .build()
  try {
    return taskScope(name = "http request", timeout = timeout) {
      val response = fork("http ${request.method()}") {
        client.send(identityRequest, HttpResponse.BodyHandlers.ofString())
      }
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
