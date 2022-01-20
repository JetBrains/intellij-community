package com.intellij.compiler.cache.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.StreamUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.io.HttpRequests;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.GZIPInputStream;

public final class CompilerCachesServerClient {
  private static final Logger LOG = Logger.getInstance(CompilerCachesServerClient.class);
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

  @NotNull
  public static Map<String, Set<String>> getCacheKeysPerRemote(@NotNull Project project, @NotNull String serverUrl) {
    Map<String, List<String>> response = doGetRequest(project, serverUrl);
    if (response == null) return Collections.emptyMap();
    Map<String, Set<String>> result = new HashMap<>();
    response.forEach((key, value) -> {
      String[] splittedRemoteUrl = key.split("/");
      result.put(splittedRemoteUrl[splittedRemoteUrl.length - 1], new HashSet<>(value));
    });
    return result;
  }


  private static @Nullable Map<String, List<String>> doGetRequest(@NotNull Project project, @NotNull String serverUrl) {
    Map<String, String> headers = CompilerCacheServerAuthUtil.getRequestHeaders(project);
    if (headers.isEmpty()) return null;
    try {
      return HttpRequests.request(serverUrl + "/commit_history.json")
        .tuner(tuner -> headers.forEach((k, v) -> tuner.addRequestProperty(k, v)))
        .connect(it -> {
          URLConnection connection = it.getConnection();
          if (connection instanceof HttpURLConnection) {
            HttpURLConnection httpConnection = (HttpURLConnection)connection;
            if (httpConnection.getResponseCode() == 200) {
              return OBJECT_MAPPER.readValue(getInputStream(httpConnection), new TypeReference<>() {
              });
            }
            else {
              String statusLine = httpConnection.getResponseCode() + ' ' + httpConnection.getRequestMethod();
              String errorText = StreamUtil.readText(new InputStreamReader(httpConnection.getErrorStream(), StandardCharsets.UTF_8));
              LOG.info("Request: " + httpConnection.getRequestMethod() + httpConnection.getURL() + " : Error " + statusLine + " body: " + errorText);
            }
          }
          return null;
        });
    }
    catch (IOException e) {
      LOG.warn("Failed request to cache server", e);
    }
    return null;
  }

  private static InputStream getInputStream(HttpURLConnection httpConnection) throws IOException {
    String contentEncoding = httpConnection.getContentEncoding();
    InputStream inputStream = httpConnection.getInputStream();
    if (contentEncoding != null && StringUtil.toLowerCase(contentEncoding).contains("gzip")) {
      return new GZIPInputStream(inputStream);
    }
    return inputStream;
  }
}
