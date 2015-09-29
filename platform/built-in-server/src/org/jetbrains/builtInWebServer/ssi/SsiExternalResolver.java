/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.builtInWebServer.ssi;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import gnu.trove.THashMap;
import io.netty.handler.codec.http.HttpRequest;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.builtInWebServer.PathInfo;
import org.jetbrains.builtInWebServer.WebServerPathToFileManager;

import java.io.File;
import java.util.Collection;
import java.util.Map;

public final class SsiExternalResolver {
  private final String[] VARIABLE_NAMES = {"AUTH_TYPE", "CONTENT_LENGTH",
    "CONTENT_TYPE", "DOCUMENT_NAME", "DOCUMENT_URI",
    "GATEWAY_INTERFACE", "HTTP_ACCEPT", "HTTP_ACCEPT_ENCODING",
    "HTTP_ACCEPT_LANGUAGE", "HTTP_CONNECTION", "HTTP_HOST",
    "HTTP_REFERER", "HTTP_USER_AGENT", "PATH_INFO", "PATH_TRANSLATED",
    "QUERY_STRING", "QUERY_STRING_UNESCAPED", "REMOTE_ADDR",
    "REMOTE_HOST", "REMOTE_PORT", "REMOTE_USER", "REQUEST_METHOD",
    "REQUEST_URI", "SCRIPT_FILENAME", "SCRIPT_NAME", "SERVER_ADDR",
    "SERVER_NAME", "SERVER_PORT", "SERVER_PROTOCOL", "SERVER_SOFTWARE",
    "UNIQUE_ID"};

  private final Project project;
  private final HttpRequest request;

  private Map<String, String> variables = new THashMap<String, String>();
  private final String parentPath;
  @NotNull private final File parentFile;

  public SsiExternalResolver(@NotNull Project project,
                             @NotNull HttpRequest request,
                             @NotNull String parentPath,
                             @NotNull File parentFile) {
    this.project = project;
    this.request = request;
    this.parentPath = parentPath;
    this.parentFile = parentFile;
  }

  public void addVariableNames(@NotNull Collection<String> variableNames) {
    for (String variableName : VARIABLE_NAMES) {
      String variableValue = getVariableValue(variableName);
      if (variableValue != null) {
        variableNames.add(variableName);
      }
    }
  }

  public void setVariableValue(@NotNull String name, String value) {
    variables.put(name, value);
  }

  public String getVariableValue(@NotNull String name) {
    String value = variables.get(name);
    return value == null ? request.headers().get(name) : value;
  }

  @Nullable
  public File findFile(@NotNull String originalPath, boolean virtual) {
    String path = FileUtil.toCanonicalPath(originalPath, '/');
    if (!virtual) {
      return new File(parentFile, path);
    }

    path = path.charAt(0) == '/' ? path : (parentPath + '/' + path);
    PathInfo pathInfo = WebServerPathToFileManager.getInstance(project).getPathInfo(path, true);
    if (pathInfo == null) {
      return null;
    }
    if (pathInfo.getIoFile() != null) {
      return pathInfo.getIoFile();
    }
    return new File(pathInfo.getFile().getPath());
  }

  public long getFileLastModified(String path, boolean virtual) {
    File file = findFile(path, virtual);
    return file == null || !file.exists() ? 0 : file.lastModified();
  }

  public long getFileSize(@NotNull String path, boolean virtual) {
    File file = findFile(path, virtual);
    return file == null || !file.exists() ? -1 : file.length();
  }
}