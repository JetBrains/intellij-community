// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.impl.compilation

import com.google.gson.Gson
import com.intellij.openapi.util.io.StreamUtil
import com.intellij.openapi.util.text.StringUtil
import groovy.transform.CompileStatic
import org.apache.http.Consts
import org.apache.http.HttpStatus
import org.apache.http.client.methods.*
import org.apache.http.entity.ContentType
import org.apache.http.entity.FileEntity
import org.apache.http.entity.StringEntity
import org.apache.http.impl.client.CloseableHttpClient
import org.apache.http.impl.client.HttpClientBuilder
import org.apache.http.impl.client.LaxRedirectStrategy
import org.apache.http.util.EntityUtils
import org.jetbrains.annotations.NotNull
import org.jetbrains.intellij.build.BuildMessages
import org.jetbrains.intellij.build.impl.retry.Retry

@CompileStatic
class CompilationPartsUploader implements Closeable {
  private final BuildMessages myMessages
  protected final String myServerUrl
  protected final CloseableHttpClient myHttpClient

  CompilationPartsUploader(@NotNull String serverUrl, @NotNull BuildMessages messages) {
    myServerUrl = fixServerUrl(serverUrl)
    myMessages = messages
    CompilationPartsUtil.initLog4J(messages)
    myHttpClient = HttpClientBuilder.create()
      .setUserAgent('Parts Uploader')
      .setRedirectStrategy(LaxRedirectStrategy.INSTANCE)
      .setMaxConnTotal(20)
      .setMaxConnPerRoute(10)
      .build()
  }

  @Override
  void close() throws IOException {
    StreamUtil.closeStream(myHttpClient)
    CompilationPartsUtil.deinitLog4J()
  }

  @SuppressWarnings("unused")
  protected void debug(String message) {
    myMessages.debug(message)
  }

  @SuppressWarnings("unused")
  protected void log(String message) {
    myMessages.info(message)
  }

  @SuppressWarnings("unused")
  protected void error(String message) {
    myMessages.error(message)
  }

  boolean upload(@NotNull final String path, @NotNull final File file, boolean sendHead) throws UploadException {
    debug("Preparing to upload " + file + " to " + myServerUrl)

    if (!file.exists()) {
      throw new UploadException("The file " + file.getPath() + " does not exist")
    }
    if (sendHead) {
      int code = doHead(path)
      if (code == HttpStatus.SC_OK) {
        log("File '$path' already exist on server, nothing to upload")
        return false
      }
      if (code != HttpStatus.SC_NOT_FOUND) {
        error("HEAD $path responded with unexpected $code")
      }
    }
    final String response = doPut(path, file)
    if (StringUtil.isEmptyOrSpaces(response)) {
      log("Performed '$path' upload.")
    }
    else {
      debug("Performed '$path' upload. Server answered: " + response)
    }
    return true
  }

  @CompileStatic
  static class CheckFilesResponse {
    List<String> found
    List<String> missing

    CheckFilesResponse() {
    }
  }

  CheckFilesResponse getFoundAndMissingFiles(String metadataJson) {
    String path = '/check-files'

    CloseableHttpResponse response = null
    String responseString = null
    try {
      String url = myServerUrl + StringUtil.trimStart(path, '/')
      debug("POST " + url)

      def request = new HttpPost(url)
      request.setEntity(new StringEntity(metadataJson, ContentType.APPLICATION_JSON))

      response = executeWithRetry(request)

      responseString = EntityUtils.toString(response.getEntity(), ContentType.APPLICATION_JSON.charset)

      def parsedResponse = new Gson().fromJson(responseString, CheckFilesResponse.class)
      return parsedResponse
    }
    catch (Exception e) {
      def additionalMessage = responseString == null ? "" : "\nResponse: $responseString"
      myMessages.warning("Failed to check for found and mising files ('$path'): ${e.message}" + additionalMessage)
      return null
    }
    finally {
      StreamUtil.closeStream(response)
    }
  }

  @NotNull
  protected int doHead(String path) throws UploadException {
    CloseableHttpResponse response = null
    try {
      String url = myServerUrl + StringUtil.trimStart(path, '/')
      debug("HEAD " + url)

      def request = new HttpGet(url)
      response = executeWithRetry(request)
      return response.getStatusLine().getStatusCode()
    }
    catch (Exception e) {
      throw new UploadException("Failed to HEAD $path: " + e.getMessage(), e)
    }
    finally {
      StreamUtil.closeStream(response)
    }
  }

  @NotNull
  private String doPut(String path, File file) throws UploadException {
    CloseableHttpResponse response = null
    try {
      String url = myServerUrl + StringUtil.trimStart(path, '/')
      debug("PUT " + url)

      def request = new HttpPut(url)
      request.setEntity(new FileEntity(file, ContentType.APPLICATION_OCTET_STREAM))

      response = executeWithRetry(request)

      def statusCode = response.statusLine.statusCode
      if (statusCode < 200  || statusCode >= 400) {
        def responseString = EntityUtils.toString(response.getEntity(), Consts.UTF_8)
        myMessages.error("PUT $url failed with $statusCode: $responseString")
      }

      EntityUtils.consume(response.getEntity())
      return statusCode
    }
    catch (Exception e) {
      throw new UploadException("Failed to PUT file to $path: " + e.getMessage(), e)
    }
    finally {
      StreamUtil.closeStream(response)
    }
  }

  CloseableHttpResponse executeWithRetry(HttpUriRequest request) {
    return new Retry(myMessages).call {
      myHttpClient.execute(request)
    }
  }

  private static String fixServerUrl(String serverUrl) {
    String url = serverUrl
    if (!url.startsWith("http://") && !url.startsWith("https://")) {
      url = "http://" + url
    }
    if (!url.endsWith("/")) url += '/'
    return url
  }
}
