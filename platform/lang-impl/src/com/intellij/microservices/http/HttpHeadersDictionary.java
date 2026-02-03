// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.microservices.http;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.intellij.microservices.mime.MimeTypes;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;

@ApiStatus.Internal
public final class HttpHeadersDictionary {
  // https://en.wikipedia.org/wiki/HTTP_compression
  private static final List<String> ENCODINGS =
    List.of("compress", "deflate", "exi", "gzip", "identity", "pack200-gzip", "br", "bzip2", "lzma", "peerdist", "sdch", "xpress", "xz");

  private static final List<String> knownExtraHeaders = List.of(
    "X-Correlation-ID",
    "X-Csrf-Token",
    "X-Forwarded-For",
    "X-Forwarded-Host",
    "X-Forwarded-Proto",
    "X-Http-Method-Override",
    "X-Request-ID",
    "X-Requested-With",
    "X-Total-Count",
    "X-User-Agent"
  );

  private static Map<String, HttpHeaderDocumentation> ourHeaders = null;
  private static Map<String, List<String>> ourHeaderValues;
  private static final Map<String, List<String>> ourHeaderOptionNames = new HashMap<>();

  static {
    ourHeaderOptionNames.put("Content-Type", List.of("charset", "boundary"));
  }

  public static synchronized @NotNull Map<String, HttpHeaderDocumentation> getHeaders() {
    if (ourHeaders == null) {
      Map<String, HttpHeaderDocumentation> headers = readHeaders();
      for (String extraHeader : knownExtraHeaders) {
        headers.put(extraHeader, new HttpHeaderDocumentation(extraHeader));
      }
      ourHeaders = headers;
    }
    return ourHeaders;
  }

  public static @Nullable HttpHeaderDocumentation getDocumentation(@NotNull String fieldName) {
    final Map<String, HttpHeaderDocumentation> headers = getHeaders();
    return headers.get(fieldName);
  }

  private static @NotNull Map<String, HttpHeaderDocumentation> readHeaders() {
    Map<String, HttpHeaderDocumentation> result = new HashMap<>();
    InputStream stream = HttpHeadersDictionary.class.getResourceAsStream("/com/intellij/microservices/http/headers-doc.json");
    try {
      String file = stream != null ? FileUtil.loadTextAndClose(stream) : "";

      if (StringUtil.isNotEmpty(file)) {
        JsonElement root = JsonParser.parseString(file);
        if (root.isJsonArray()) {
          JsonArray array = root.getAsJsonArray();
          for (JsonElement element : array) {
            if (element.isJsonObject()) {
              HttpHeaderDocumentation header = HttpHeaderDocumentation.read(element.getAsJsonObject());
              if (header != null) {
                result.put(header.getName(), header);
              }
            }
          }
        }
      }
    }
    catch (IOException e) {
      Logger.getInstance(HttpHeadersDictionary.class).error(e);
    }
    return result;
  }

  public static @NotNull Collection<String> getHeaderValues(@NotNull Project project, @NotNull String headerName) {
    if (ourHeaderValues == null) {
      ourHeaderValues = readHeaderValues();
    }

    return ourHeaderValues.containsKey(headerName) ? ourHeaderValues.get(headerName) : ContainerUtil.emptyList();
  }

  public static @NotNull Collection<String> getHeaderOptionNames(@NotNull String headerName) {
    return ourHeaderOptionNames.containsKey(headerName) ? ourHeaderOptionNames.get(headerName) : ContainerUtil.emptyList();
  }

  private static @NotNull Map<String, List<String>> readHeaderValues() {
    //TODO: read header values from file
    List<String> mimeTypes = Arrays.asList(MimeTypes.PREDEFINED_MIME_VARIANTS);
    Map<String, List<String>> values = new HashMap<>();
    values.put("Accept", mimeTypes);
    values.put("Content-Type", mimeTypes);
    values.put("Accept-Encoding", ENCODINGS);
    return values;
  }
}
