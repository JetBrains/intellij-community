// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.logsUploader;

import com.fasterxml.jackson.jr.ob.JSON;
import com.intellij.ide.IdeBundle;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.progress.ProgressIndicatorProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.StreamUtil;
import com.intellij.util.io.HttpRequests;
import com.intellij.util.net.NetUtils;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static com.intellij.ide.actions.CollectZippedLogsActionKt.COLLECT_LOGS_NOTIFICATION_GROUP;

@ApiStatus.Internal
public final class LogUploader {
  private static final String SERVICE_URL = "https://uploads.jetbrains.com";
  private static final String BYTES_CONTENT_TYPE = "application/octet-stream";
  private static final String JSON_CONTENT_TYPE = "application/json";

  public static @NotNull String uploadFile(@NotNull Path file) throws IOException {
    return uploadFile(file, file.getFileName().toString());
  }

  public static @NotNull String uploadFile(@NotNull Path file, @NotNull String fileName) throws IOException {
    var requestObj = JSON.std.asString(Map.of(
      "filename", fileName,
      "method", "put",
      "contentType", BYTES_CONTENT_TYPE
    ));
    var responseObj = HttpRequests.post(SERVICE_URL + "/sign", JSON_CONTENT_TYPE + "; charset=utf-8")
      .accept(JSON_CONTENT_TYPE)
      .connect(request -> {
        request.write(requestObj);
        var response = StreamUtil.readText(request.getReader());
        return JSON.std.mapFrom(response);
      });

    var uploadUrl = responseObj.get("url").toString();
    @SuppressWarnings("unchecked")
    var headers = (Map<String, String>)responseObj.get("headers");
    var id = responseObj.get("folderName").toString();

    @SuppressWarnings("UsagesOfObsoleteApi")
    var indicator = ProgressIndicatorProvider.getGlobalProgressIndicator();
    HttpRequests.put(uploadUrl, "application/octet-stream")
      .productNameAsUserAgent()
      .tuner(urlConnection -> headers.forEach((k, v) -> urlConnection.addRequestProperty(k, v)))
      .connect(it -> {
        var http = ((HttpURLConnection)it.getConnection());
        var length = Files.size(file);
        http.setFixedLengthStreamingMode(length);
        try (var outputStream = http.getOutputStream(); var inputStream = new BufferedInputStream(Files.newInputStream(file), 64 * 1024)) {
          NetUtils.copyStreamContent(indicator, inputStream, outputStream, length);
        }
        return null;
      });

    return id;
  }

  public static void notify(@Nullable Project project, @NotNull String id) {
    var message = IdeBundle.message("collect.logs.notification.sent.success", SERVICE_URL, id);
    new Notification(COLLECT_LOGS_NOTIFICATION_GROUP, message, NotificationType.INFORMATION)
      .notify(project);
  }

  public static @NotNull String getBrowseUrl(@NotNull String id) {
    return SERVICE_URL + "/browse#" + id;
  }
}
