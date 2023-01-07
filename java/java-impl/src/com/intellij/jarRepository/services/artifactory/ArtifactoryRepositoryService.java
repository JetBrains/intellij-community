// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.jarRepository.services.artifactory;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.intellij.jarRepository.RemoteRepositoryDescription;
import com.intellij.jarRepository.RepositoryArtifactDescription;
import com.intellij.jarRepository.services.MavenRepositoryService;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.Url;
import com.intellij.util.io.HttpRequests;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * @author Gregory.Shrago
 */

//use some endpoints from http://repo.jfrog.org/artifactory/api/
public class ArtifactoryRepositoryService extends MavenRepositoryService {

  private final Gson gson = new Gson();

  @NotNull
  @Override
  public String getDisplayName() {
    return "Artifactory";
  }

  @NotNull
  @Override
  public List<RemoteRepositoryDescription> getRepositories(@NotNull String url) throws IOException {
    ApplicationManager.getApplication().assertIsNonDispatchThread();
    try {
      ArtifactoryModel.RepositoryType[] repos = gson.fromJson(
        HttpRequests.request(toUrl(url, "repositories"))
          .productNameAsUserAgent()
          .readString(),
        ArtifactoryModel.RepositoryType[].class
      );
      final List<RemoteRepositoryDescription> result = new ArrayList<>(repos.length);
      for (ArtifactoryModel.RepositoryType repo : repos) {
        result.add(convert(repo));
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

  private static RemoteRepositoryDescription convert(ArtifactoryModel.RepositoryType repo) {
    return new RemoteRepositoryDescription(repo.key, ObjectUtils.notNull(repo.description, repo.key), repo.url);
  }

  @NotNull
  @Override
  public List<RepositoryArtifactDescription> findArtifacts(@NotNull String url, @NotNull RepositoryArtifactDescription template)
    throws IOException {
    ApplicationManager.getApplication().assertIsNonDispatchThread();
    try {


      final String className = template.getClassNames();
      if (className == null || className.length() == 0) {
        return searchArtifacts(url, template);
      }
      else {
        return searchArchives(url, template);
      }
    }
    catch (JsonSyntaxException e) {
      return Collections.emptyList();
    }
    catch (Exception e) {
      throw new IOException(e);
    }
  }

  @NotNull
  private List<RepositoryArtifactDescription> searchArchives(@NotNull String url, @NotNull RepositoryArtifactDescription template)
    throws IOException {
    final String packaging = StringUtil.notNullize(template.getPackaging());
    final ArrayList<RepositoryArtifactDescription> artifacts = new ArrayList<>();

    String className = template.getClassNames();
    final String searchString = className.endsWith("*") || className.endsWith("?") ? className : className + ".class";
    Url requestUrl = toUrl(url, "search/archive", "name=" + URLEncoder.encode(searchString.trim(), StandardCharsets.UTF_8));
    ArtifactoryModel.ArchiveResults results = gson.fromJson(
      HttpRequests.request(requestUrl)
        .productNameAsUserAgent().readString(),
      ArtifactoryModel.ArchiveResults.class
    );

    if (results != null && results.results != null) {
      for (ArtifactoryModel.ArchiveResult result : results.results) {
        for (String uri : result.archiveUris) {
          if (!uri.endsWith(packaging)) continue;
          artifacts.add(convertArtifactInfo(uri, url, result.entry));
        }
      }
    }

    return artifacts;
  }

  @NotNull
  private List<RepositoryArtifactDescription> searchArtifacts(@NotNull String url, @NotNull RepositoryArtifactDescription template)
    throws IOException {
    final String packaging = StringUtil.notNullize(template.getPackaging());
    final ArrayList<RepositoryArtifactDescription> artifacts = new ArrayList<>();

    Map<String, String> params = new LinkedHashMap<>();
    params.put("g", template.getGroupId());
    params.put("a", template.getArtifactId());
    params.put("v", template.getVersion());
    params.put("repos", "");
    Url requestUrl = toUrl(url, "search/gavc", mapToParamString(params));
    ArtifactoryModel.GavcResults results = gson.fromJson(
      HttpRequests.request(requestUrl)
        .productNameAsUserAgent()
        .readString(),
      ArtifactoryModel.GavcResults.class
    );

    if (results != null && results.results != null) {
      for (ArtifactoryModel.GavcResult result : results.results) {
        if (!result.uri.endsWith(packaging)) continue;
        artifacts.add(convertArtifactInfo(result.uri, url, null));
      }
    }

    return artifacts;
  }

  private static RepositoryArtifactDescription convertArtifactInfo(String uri, String baseUri, String className) {
    final String repoPathFile = uri.substring((baseUri + "storage/").length());
    final int repoIndex = repoPathFile.indexOf('/');
    final String repoString = repoPathFile.substring(0, repoIndex);
    final String repo = repoString.endsWith("-cache") ? repoString.substring(0, repoString.lastIndexOf('-')) : repoString;
    final String filePath = repoPathFile.substring(repoIndex + 1, repoPathFile.lastIndexOf('/'));
    final int artIdIndex = filePath.lastIndexOf('/');
    final String version = filePath.substring(artIdIndex + 1);
    final String groupArtifact = filePath.substring(0, artIdIndex);
    final int groupIndex = groupArtifact.lastIndexOf('/');
    final String artifact = groupArtifact.substring(groupIndex + 1);
    final String group = groupArtifact.substring(0, groupIndex).replace('/', '.');
    final String packaging = uri.substring(uri.lastIndexOf('.') + 1);

    return new RepositoryArtifactDescription(group, artifact, version, packaging, null, className, repo);
  }
}
