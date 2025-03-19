// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.jarRepository.services;

import com.intellij.jarRepository.RemoteRepositoryDescription;
import com.intellij.jarRepository.RepositoryArtifactDescription;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.Url;
import com.intellij.util.Urls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

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
  public abstract @NotNull String getDisplayName();

  public abstract @Unmodifiable @NotNull List<RemoteRepositoryDescription> getRepositories(@NotNull String url) throws IOException;

  public abstract @NotNull List<RepositoryArtifactDescription> findArtifacts(@NotNull String url, @NotNull RepositoryArtifactDescription template)
    throws IOException;


  @Override
  public final String toString() {
    return getDisplayName();
  }


  protected @NotNull String mapToParamString(@NotNull Map<String, String> params) {
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
