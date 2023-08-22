// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.jarRepository.services.nexus;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.intellij.jarRepository.RemoteRepositoryDescription;
import com.intellij.jarRepository.RepositoryArtifactDescription;
import com.intellij.jarRepository.services.MavenRepositoryService;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.SmartList;
import com.intellij.util.io.HttpRequests;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.*;

/**
 * @author Gregory.Shrago
 */
public class NexusRepositoryService extends MavenRepositoryService {

  private final Gson gson = new Gson();

  @Nullable
  public static RemoteRepositoryDescription convertRepositoryInfo(@NotNull NexusModel.RepositoryType repo) {
    if (repo.id == null) {
      return null;
    }
    if (repo.name == null) {
      return null;
    }
    if (repo.contentResourceURI == null) {
      return null;
    }
    return new RemoteRepositoryDescription(repo.id, repo.name, repo.contentResourceURI);
  }

  public static RepositoryArtifactDescription convertArtifactInfo(NexusModel.ArtifactType t) {
    return new RepositoryArtifactDescription(
      t.groupId, t.artifactId, t.version, t.packaging, t.classifier, null, t.repoId
    );
  }

  @NotNull
  @Override
  public String getDisplayName() {
    return "Nexus";
  }

  @NotNull
  @Override
  public List<RemoteRepositoryDescription> getRepositories(@NotNull String url) throws IOException {
    ApplicationManager.getApplication().assertIsNonDispatchThread();
    try {
      NexusModel.RepositorySearchResults repos = gson.fromJson(
        HttpRequests.request(toUrl(url, "repositories"))
          .productNameAsUserAgent()
          .accept("application/json")
          .readString(), NexusModel.RepositorySearchResults.class);
      final List<RemoteRepositoryDescription> result = new SmartList<>();
      if (repos.data != null) {
        for (NexusModel.RepositoryType repo : repos.data) {
          if ("maven2".equals(repo.provider)) {
            final RemoteRepositoryDescription desc = convertRepositoryInfo(repo);
            if (desc != null) {
              result.add(desc);
            }
          }
        }
      }
      return result;
    }
    catch (JsonSyntaxException e) {
      return Collections.emptyList();
    }
    catch (Exception e) {
      throw new IOException(e);
    }
  }

  @NotNull
  @Override
  public List<RepositoryArtifactDescription> findArtifacts(@NotNull String url, @NotNull RepositoryArtifactDescription template) throws IOException {
    ApplicationManager.getApplication().assertIsNonDispatchThread();
    try {
      final String packaging = StringUtil.notNullize(template.getPackaging());

      NexusModel.ArtifactSearchResults results =
        gson.fromJson(HttpRequests.request(toUrl(url, "data_index", prepareParameters(template)))
                        .accept("application/json")
                        .readString(), NexusModel.ArtifactSearchResults.class);

      //boolean tooManyResults = results.isTooManyResults();
      final ArrayList<RepositoryArtifactDescription> result = new ArrayList<>();
      if (results.data != null) {
        for (NexusModel.ArtifactType each : results.data) {
          if (Objects.equals(each.packaging, packaging)) {
            result.add(convertArtifactInfo(each));
          }
        }
      }
      return result;
    }
    catch (JsonSyntaxException e) {
      return Collections.emptyList();
    }
    catch (Exception e) {
      throw new IOException(e);
    }
  }

  @NotNull
  private String prepareParameters(@NotNull RepositoryArtifactDescription template) {
    Map<String, String> params = new LinkedHashMap<>();
    params.put("q", StringUtil.join(Arrays.asList(template.getGroupId(), template.getArtifactId(), template.getVersion()), ":"));
    params.put("g", template.getGroupId());
    params.put("a", template.getArtifactId());
    params.put("v", template.getVersion());
    params.put("cn", template.getClassNames());

    return mapToParamString(params);
  }
}
