// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.logsUploader;

import com.fasterxml.jackson.jr.ob.JSON;
import com.intellij.ide.IdeBundle;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.project.Project;
import com.intellij.util.net.PlatformHttpClient;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
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
    try {
      var client = PlatformHttpClient.client();

      var requestObj = JSON.std.asString(Map.of(
        "filename", fileName,
        "method", "put",
        "contentType", BYTES_CONTENT_TYPE
      ));
      var request = PlatformHttpClient.requestBuilder(new URI(SERVICE_URL + "/sign"))
        .header("Content-Type", JSON_CONTENT_TYPE + "; charset=utf-8")
        .header("Accept", JSON_CONTENT_TYPE)
        .POST(HttpRequest.BodyPublishers.ofString(requestObj))
        .build();
      var response = PlatformHttpClient.checkResponse(client.send(request, HttpResponse.BodyHandlers.ofString()));
      var responseObj = JSON.std.mapFrom(response.body());

      var uploadUrl = responseObj.get("url").toString();
      @SuppressWarnings("unchecked")
      var headers = (Map<String, String>)responseObj.get("headers");
      var id = responseObj.get("folderName").toString();

      var builder = PlatformHttpClient.requestBuilder(new URI(uploadUrl));
      headers.forEach(builder::header);
      request = builder
        .header("Content-Type", BYTES_CONTENT_TYPE)
        .PUT(HttpRequest.BodyPublishers.ofFile(file))
        .build();
      PlatformHttpClient.checkResponse(client.send(request, HttpResponse.BodyHandlers.discarding()));

      return id;
    }
    catch (URISyntaxException e) {
      throw new IOException(e);
    }
    catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
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
