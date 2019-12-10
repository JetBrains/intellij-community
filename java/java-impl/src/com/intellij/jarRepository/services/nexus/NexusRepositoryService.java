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
package com.intellij.jarRepository.services.nexus;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.intellij.jarRepository.RemoteRepositoryDescription;
import com.intellij.jarRepository.RepositoryArtifactDescription;
import com.intellij.jarRepository.services.MavenRepositoryService;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.SmartList;
import com.intellij.util.io.HttpRequests;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
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
    assert !ApplicationManager.getApplication().isDispatchThread();
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
    assert !ApplicationManager.getApplication().isDispatchThread();
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
          if (Comparing.equal(each.packaging, packaging)) {
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
