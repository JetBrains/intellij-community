// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.impl

import com.intellij.openapi.util.text.StringUtil
import groovy.transform.CompileStatic
import org.jetbrains.annotations.NotNull
import org.jetbrains.intellij.build.BuildMessages

@CompileStatic
class CompilationPartsUploader {
  private final String myServerUrl
  private final BuildMessages myMessages

  private static final String UTF_8 = "UTF-8"
  private static final int MB = 1024 * 1024

  static class UploadException extends Exception {
    UploadException(String message) {
      super(message)
    }

    UploadException(String message, Throwable cause) {
      super(message, cause)
    }
  }

  CompilationPartsUploader(@NotNull String serverUrl, @NotNull BuildMessages messages) {
    myServerUrl = fixServerUrl(serverUrl)
    myMessages = messages
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

  boolean upload(@NotNull final String path, @NotNull final File file) throws UploadException {
    debug("Preparing to upload " + file + " to " + myServerUrl)

    if (!file.exists()) {
      throw new UploadException("The file " + file.getPath() + " does not exist")
    }

    int code = doHead(path)
    if (code == 200) {
      log("File '$path' already exist on server, nothing to upload")
      return false
    }
    if (code != 404) {
      error("HEAD $path responded with unexpected $code")
    }

    final String response = doPut(path, file)
    if (StringUtil.isEmptyOrSpaces(response)) {
      log("Performed '$path' upload.")
    } else {
      debug("Performed '$path' upload. Server answered: " + response)
    }
    return true
  }

  @NotNull
  private int doHead(String path) throws UploadException {
    try {
      String url = myServerUrl + StringUtil.trimStart(path, '/')
      debug("HEAD " + url)

      HttpURLConnection conn = (HttpURLConnection)new URL(url).openConnection()
      conn.setDoInput(true)
      conn.setDoOutput(false)
      conn.setUseCaches(false)
      conn.setInstanceFollowRedirects(true)
      conn.setRequestMethod("HEAD")

      conn.setRequestProperty("Connection", "Keep-Alive")
      conn.setRequestProperty("Accept", "*/*")

      // Get the response
      final int code = conn.getResponseCode()
      return code
    }
    catch (Exception e) {
      throw new UploadException("Failed to HEAD $path: " + e.getMessage(), e)
    }
  }

  @NotNull
  private String doPut(String path, File file) throws UploadException {
    try {
      String url = myServerUrl + StringUtil.trimStart(path, '/')
      debug("PUT " + url)

      HttpURLConnection conn = (HttpURLConnection)new URL(url).openConnection()
      conn.setDoInput(true)
      conn.setDoOutput(true)
      conn.setUseCaches(false)
      conn.setRequestMethod("PUT")

      conn.setRequestProperty("Connection", "Keep-Alive")
      conn.setRequestProperty("Accept-Charset", UTF_8)
      conn.setRequestProperty("Content-Type", "application/octet-stream")
      conn.setRequestProperty("Content-Length", String.valueOf(file.length()))
      conn.setFixedLengthStreamingMode(file.length())

      InputStream is = new BufferedInputStream(new FileInputStream(file), 5 * MB)
      OutputStream output = conn.getOutputStream()
      transfer(is, output, MB)
      is.close()
      output.close()

      // Get the response
      return readBody(conn)
    }
    catch (Exception e) {
      throw new UploadException("Failed to PUT file to $path: " + e.getMessage(), e)
    }
  }

  @NotNull
  private static String readBody(HttpURLConnection conn) throws IOException {
    final InputStream response = conn.getInputStream()
    final ByteArrayOutputStream output = new ByteArrayOutputStream()
    transfer(response, output, 8 * 1024)
    response.close()
    return new String(output.toByteArray(), UTF_8)
  }


  private static String fixServerUrl(String serverUrl) {
    String url = serverUrl
    if (!url.startsWith("http://") && !url.startsWith("https://")) {
      url = "http://" + url
    }
    if (!url.endsWith("/")) url += '/'
    return url
  }

  private static void transfer(InputStream is, OutputStream output, int bufferSize) throws IOException {
    byte[] buffer = new byte[bufferSize]
    int length
    while ((length = is.read(buffer)) > 0) {
      output.write(buffer, 0, length)
    }
    output.flush()
  }
}
