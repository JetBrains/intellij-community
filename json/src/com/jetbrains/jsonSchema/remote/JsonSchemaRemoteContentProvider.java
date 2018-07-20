// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.jsonSchema.remote;

import com.intellij.json.JsonFileType;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.impl.http.DefaultRemoteContentProvider;
import com.intellij.util.Url;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.io.HttpRequests;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.net.URLConnection;
import java.nio.file.Files;
import java.time.Duration;
import java.util.List;

public class JsonSchemaRemoteContentProvider extends DefaultRemoteContentProvider {
  private static final int DEFAULT_CONNECT_TIMEOUT = 10000;
  private static final long UPDATE_DELAY = Duration.ofHours(4).toMillis();
  static final String STORE_URL_PREFIX_HTTP = "http://json.schemastore.org";
  static final String STORE_URL_PREFIX_HTTPS = "https://schemastore.azurewebsites.net";
  private static final String SCHEMA_URL_PREFIX = "http://json-schema.org/";
  private static final String ETAG_HEADER = "ETag";
  private static final String LAST_MODIFIED_HEADER = "Last-Modified";

  private long myLastUpdateTime = 0;

  @Override
  public boolean canProvideContent(@NotNull Url url) {
    String externalForm = url.toExternalForm();
    return externalForm.startsWith(STORE_URL_PREFIX_HTTP)
           || externalForm.startsWith(STORE_URL_PREFIX_HTTPS)
           || externalForm.startsWith(SCHEMA_URL_PREFIX)
           || externalForm.endsWith(".json");
  }

  @Override
  protected void saveAdditionalData(@NotNull HttpRequests.Request request, @NotNull File file) throws IOException {
    URLConnection connection = request.getConnection();
    if (saveTag(file, connection, ETAG_HEADER)) return;
    saveTag(file, connection, LAST_MODIFIED_HEADER);
  }

  @Nullable
  @Override
  protected FileType adjustFileType(@Nullable FileType type, @NotNull Url url) {
    if (type == null && url.toExternalForm().startsWith(SCHEMA_URL_PREFIX)) {
      // json-schema.org doesn't provide a mime-type for schemas
      return JsonFileType.INSTANCE;
    }
    return super.adjustFileType(type, url);
  }

  private static boolean saveTag(@NotNull File file, @NotNull URLConnection connection, @NotNull String header) throws IOException {
    String tag = connection.getHeaderField(header);
    if (tag != null) {
      String path = file.getAbsolutePath();
      if (!path.endsWith(".json")) path += ".json";
      File tagFile = new File(path + "." + header);
      saveToFile(tagFile, tag);
      return true;
    }
    return false;
  }

  private static void saveToFile(@NotNull File tagFile, @NotNull String headerValue) throws IOException {
    if (!tagFile.exists()) if (!tagFile.createNewFile()) return;
    Files.write(tagFile.toPath(), ContainerUtil.createMaybeSingletonList(headerValue));
  }

  public boolean isUpToDate(@NotNull Url url, @NotNull VirtualFile local) {
    long now = System.currentTimeMillis();
    // don't update more frequently than once in 4 hours
    if (now - myLastUpdateTime < UPDATE_DELAY) {
      return true;
    }

    myLastUpdateTime = now;
    String path = local.getPath();

    if (now - new File(path).lastModified() < UPDATE_DELAY) {
      return true;
    }

    if (checkUpToDate(url, path, ETAG_HEADER)) return true;
    if (checkUpToDate(url, path, LAST_MODIFIED_HEADER)) return true;

    return false;
  }

  private boolean checkUpToDate(@NotNull Url url, @NotNull String path, @NotNull String header) {
    File file = new File(path + "." + header);
    try {
      return isUpToDate(url, file, header);
    }
    catch (IOException e) {
      // in case of an error, don't bother with update for the next UPDATE_DELAY milliseconds
      //noinspection ResultOfMethodCallIgnored
      new File(path).setLastModified(System.currentTimeMillis());
      return true;
    }
  }

  @Override
  protected int getDefaultConnectionTimeout() {
    return DEFAULT_CONNECT_TIMEOUT;
  }

  private boolean isUpToDate(@NotNull Url url, @NotNull File file, @NotNull String header) throws IOException {
    List<String> strings = file.exists() ? Files.readAllLines(file.toPath()) : ContainerUtil.emptyList();

    String currentTag = strings.size() > 0 ? strings.get(0) : null;
    if (currentTag == null) return false;

    String remoteTag = connect(url, HttpRequests.head(url.toExternalForm()),
                               r -> r.getConnection().getHeaderField(header));

    return currentTag.equals(remoteTag);
  }
}
