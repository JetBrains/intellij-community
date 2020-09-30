/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.jarRepository.services;

import com.intellij.jarRepository.RemoteRepositoryDescription;
import com.intellij.jarRepository.RepositoryArtifactDescription;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.Url;
import com.intellij.util.Urls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * @author Gregory.Shrago
 */
public abstract class MavenRepositoryService {
  @NotNull
  public abstract String getDisplayName();

  @NotNull
  public abstract List<RemoteRepositoryDescription> getRepositories(@NotNull String url) throws IOException;

  @NotNull
  public abstract List<RepositoryArtifactDescription> findArtifacts(@NotNull String url, @NotNull RepositoryArtifactDescription template)
    throws IOException;


  public final String toString() {
    return getDisplayName();
  }


  @NotNull
  protected String mapToParamString(@NotNull Map<String, String> params) {
    return StringUtil.join(params.entrySet(), entry -> {
      if (entry.getValue() == null) {
        return null;
      }
      return entry.getKey() + "=" + URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8);
    }, "&");
  }

  protected Url toUrl(@NotNull String base, @NotNull String path) throws MalformedURLException {
    return toUrl(base, path, null);
  }

  protected Url toUrl(@NotNull String base, @NotNull String path, @Nullable String parameters) throws MalformedURLException {
    Url baseUrl = Urls.parse(base, false);
    if (baseUrl == null || baseUrl.getScheme() == null || baseUrl.getAuthority() == null) {
      throw new MalformedURLException("cannot parse " + base);
    }
    String newPath = baseUrl.getPath().endsWith("/") ? baseUrl.getPath() + path : baseUrl.getPath() + "/" + path;
    return Urls.newUrl(baseUrl.getScheme(), baseUrl.getAuthority(), newPath, parameters == null ? null : "?" + parameters);
  }
}
