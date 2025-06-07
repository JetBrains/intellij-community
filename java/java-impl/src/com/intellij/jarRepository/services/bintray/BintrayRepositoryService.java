// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.jarRepository.services.bintray;

import com.intellij.jarRepository.RemoteRepositoryDescription;
import com.intellij.jarRepository.RepositoryArtifactDescription;
import com.intellij.jarRepository.services.MavenRepositoryService;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;

import static com.intellij.openapi.util.text.StringUtil.*;
import static java.util.Collections.emptyList;

/**
 * @deprecated since Bintray service is scheduled for sunsetting in May 2021
 */
@SuppressWarnings("DeprecatedIsStillUsed") // allow to use it in 2021.1
@Deprecated(forRemoval = true)
public class BintrayRepositoryService extends MavenRepositoryService {

  @Override
  public @NotNull String getDisplayName() {
    return "Bintray";
  }

  @Override
  public @Unmodifiable @NotNull List<RemoteRepositoryDescription> getRepositories(@NotNull String url) throws IOException {
    BintrayModel.Repository info = parseInfo(url);
    if (info != null) {
      BintrayEndpoint bintrayEndpoint = new BintrayEndpoint();
      if (info.repo != null) {
        RemoteRepositoryDescription repository = bintrayEndpoint.getRepository(info.subject, info.repo);
        return ContainerUtil.createMaybeSingletonList(repository);
      } else {
        return bintrayEndpoint.getRepositories(info.subject);
      }
    }
    return emptyList();
  }

  @Override
  public @NotNull List<RepositoryArtifactDescription> findArtifacts(@NotNull String url, @NotNull RepositoryArtifactDescription template)
      throws IOException {
    if (template.getPackaging() == null || template.getPackaging().equals("jar")) {
      BintrayModel.Repository info = parseInfo(url);
      if (info != null) {
        BintrayEndpoint bintrayEndpoint = new BintrayEndpoint();
        if (isNotEmpty(template.getClassNames())) {
          return bintrayEndpoint.getArtifacts(info.subject, info.repo, template.getClassNames());
        }
        else {
          List<RepositoryArtifactDescription> artifacts =
            bintrayEndpoint.getArtifacts(info.subject, info.repo, template.getGroupId(), template.getArtifactId());
          if (!isEmpty(template.getVersion())) {
            String versionTemplate = trimStart(trimEnd(template.getVersion(), "*"), "*");
            artifacts = ContainerUtil.filter(artifacts, a -> !a.getVersion().contains(versionTemplate));
          }
          return artifacts;
        }
      }
    }
    return emptyList();
  }

  public static @Nullable BintrayModel.Repository parseInfo(String url) {
    try {
      URL theUrl = new URL(url);

      String host = theUrl.getHost();
      if (host != null) {
        List<String> path = split(trimStart(theUrl.getPath(), "/"), "/");
        if (host.equals("dl.bintray.com")) {
          if (!path.isEmpty()) {
            return new BintrayModel.Repository(path.get(0), path.size() > 1 ? path.get(1) : null);
          }
        }
        else if (host.equals("jcenter.bintray.com")) {
          return new BintrayModel.Repository("bintray", "jcenter");
        }
        else if (host.endsWith(".bintray.com")) {
          return new BintrayModel.Repository(trimEnd(host, ".bintray.com"), path.isEmpty() ? null : path.get(0));
        }
      }
    }
    catch (MalformedURLException ignored) {
    }
    return null;
  }
}
