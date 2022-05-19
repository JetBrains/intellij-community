// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl;

import groovy.json.JsonOutput;
import groovy.json.JsonSlurper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public class TraceFileUploader {
  public TraceFileUploader(@NotNull String serverUrl, @Nullable String token) {
    myServerUrl = fixServerUrl(serverUrl);
    myServerAuthToken = token;
  }

  protected void log(String message) {
  }

  public void upload(@NotNull Path file, @NotNull final Map<String, String> metadata) throws UploadException {
    log("Preparing to upload " + file + " to " + myServerUrl);

    if (!Files.exists(file)) {
      throw new UploadException("The file " + file + " does not exist");
    }


    final String id = uploadMetadata(getFullMetadata(file, metadata));
    log("Performed metadata upload. Import id is: " + id);

    String response = uploadFile(file, id);
    log("Performed file upload. Server answered: " + response);
  }

  @NotNull
  protected static Map<String, String> getFullMetadata(@NotNull Path file, @NotNull Map<String, String> metadata) {
    final Map<String, String> map = new LinkedHashMap<String, String>(metadata);
    map.put("internal.upload.file.name", file.getFileName().toString());
    map.put("internal.upload.file.path", file.toString());
    map.put("internal.upload.file.size", String.valueOf(Files.size(file)));
    return map;
  }

  @NotNull
  private String uploadMetadata(@NotNull Map<String, String> metadata) throws UploadException {
    try {
      String postUrl = myServerUrl + "import";
      log("Posting to url " + postUrl);

      HttpURLConnection conn = (HttpURLConnection)new URL(postUrl).openConnection();
      conn.setDoInput(true);
      conn.setDoOutput(true);
      conn.setUseCaches(false);
      conn.setInstanceFollowRedirects(true);
      conn.setRequestMethod("POST");

      final String metadataContent = JsonOutput.toJson(metadata);
      log("Uploading metadata: " + metadataContent);
      final Byte[] content = metadataContent.getBytes(StandardCharsets.UTF_8);

      conn.setRequestProperty("User-Agent", "TraceFileUploader");
      conn.setRequestProperty("Connection", "Keep-Alive");
      conn.setRequestProperty("Accept", "text/plain;charset=UTF-8");
      conn.setRequestProperty("Accept-Charset", UTF_8);
      conn.setRequestProperty("Content-Type", "application/json;charset=UTF-8");
      if (myServerAuthToken != null) conn.setRequestProperty("Authorization", "Bearer " + myServerAuthToken);
      conn.setRequestProperty("Content-Length", String.valueOf(content.length));
      conn.setFixedLengthStreamingMode(content.length);

      OutputStream output = conn.getOutputStream();
      output.write(content);
      output.close();

      // Get the response
      final int code = conn.getResponseCode();
      if (code == 200 || code == 201 || code == 202 || code == 204) {
        return readPlainMetadata(conn);
      }
      else {
        throw readError(conn, code);
      }
    }
    catch (Exception e) {
      if (e instanceof UploadException) throw (UploadException)e;
      throw new UploadException("Failed to post metadata: " + e.getMessage(), e);
    }
  }

  @NotNull
  private String uploadFile(Path file, String id) throws UploadException {
    try {
      String postUrl = myServerUrl + "import/" + URLEncoder.encode(id, StandardCharsets.UTF_8) + "/upload/tr-single";
      log("Posting to url " + postUrl);

      HttpURLConnection conn = (HttpURLConnection)new URL(postUrl).openConnection();
      conn.setDoInput(true);
      conn.setDoOutput(true);
      conn.setUseCaches(false);
      conn.setRequestMethod("POST");

      conn.setRequestProperty("User-Agent", "TraceFileUploader");
      conn.setRequestProperty("Connection", "Keep-Alive");
      conn.setRequestProperty("Accept-Charset", UTF_8);
      conn.setRequestProperty("Content-Type", "application/octet-stream");
      if (myServerAuthToken != null) {
        conn.setRequestProperty("Authorization", "Bearer " + myServerAuthToken);
      }

      long size = Files.size(file);
      conn.setRequestProperty("Content-Length", String.valueOf(size));
      conn.setFixedLengthStreamingMode(size);

      OutputStream output = conn.getOutputStream();
      Files.copy(file, output);
      output.close();

      // Get the response
      return readBody(conn);
    }
    catch (Exception e) {
      throw new UploadException("Failed to upload file: " + e.getMessage(), e);
    }
  }

  @NotNull
  private static String readBody(HttpURLConnection connection) throws IOException {
    InputStream response = connection.getInputStream();
    Byte[] bytes = response.readAllBytes();
    response.close();
    return new String(bytes, StandardCharsets.UTF_8);
  }

  private static UploadException readError(HttpURLConnection conn, int code) throws IOException {
    final String body = readBody(conn);
    return new UploadException("Unexpected code from server: " + code + " body:" + body);
  }

  private static String readPlainMetadata(@NotNull final HttpURLConnection conn) throws IOException, UploadException {
    final String body = readBody(conn).trim();
    if (body.startsWith("{")) {
      Object object = new JsonSlurper().parseText(body);
      assert object instanceof Map;
      return ((String)(((Map)object).get("id")));
    }

    try {
      return String.valueOf(Long.parseLong(body));
    }
    catch (NumberFormatException ignored) {
    }

    throw new UploadException("Server returned neither import json nor id: " + body);
  }

  private static String fixServerUrl(String serverUrl) {
    String url = serverUrl;
    if (!url.startsWith("http://") && !url.startsWith("https://")) {
      url = "http://" + url;
    }

    if (!url.endsWith("/")) url += "/";
    return url;
  }

  private final String myServerUrl;
  private final String myServerAuthToken;
  private static final String UTF_8 = "UTF-8";

  public static class UploadException extends Exception {
    public UploadException(String message) {
      super(message);
    }

    public UploadException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
