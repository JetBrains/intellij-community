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

import com.intellij.jarRepository.RemoteRepositoryDescription;
import com.intellij.jarRepository.RepositoryArtifactDescription;
import com.intellij.jarRepository.services.MavenRepositoryService;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.xml.bind.JAXBException;
import javax.xml.bind.UnmarshalException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * @author Gregory.Shrago
 */
public class NexusRepositoryService extends MavenRepositoryService {
  @Nullable
  public static RemoteRepositoryDescription convertRepositoryInfo(@NotNull RepositoryType repo) {
    final String id = repo.getId();
    if (id == null) {
      return null;
    }
    final String name = repo.getName();
    if (name == null) {
      return null;
    }
    final String uri = repo.getContentResourceURI();
    if (uri == null) {
      return null;
    }
    return new RemoteRepositoryDescription(id, name, uri);
  }

  public static RepositoryArtifactDescription convertArtifactInfo(ArtifactType t) {
    return new RepositoryArtifactDescription(
      t.getGroupId(), t.getArtifactId(), t.getVersion(), t.getPackaging(), t.getClassifier(), null, t.getRepoId()
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
    try {
      final List<RepositoryType> repos = new Endpoint.Repositories(url).getRepolistAsRepositories().getData().getRepositoriesItem();
      final List<RemoteRepositoryDescription> result = new SmartList<>();
      for (RepositoryType repo : repos) {
        if ("maven2".equals(repo.getProvider())){
          final RemoteRepositoryDescription desc = convertRepositoryInfo(repo);
          if (desc != null) {
            result.add(desc);
          }
        }
      }
      return result;
    }
    catch (UnmarshalException e) {
      return Collections.emptyList();
    }
    catch (JAXBException e) {
      throw new IOException(e);
    }
  }

  @NotNull
  @Override
  public List<RepositoryArtifactDescription> findArtifacts(@NotNull String url, @NotNull RepositoryArtifactDescription template) throws IOException {
    try {
      final String packaging = StringUtil.notNullize(template.getPackaging());
      final String name = StringUtil.join(Arrays.asList(template.getGroupId(), template.getArtifactId(), template.getVersion()), ":");
      final SearchResults results = new Endpoint.DataIndex(url).getArtifactlistAsSearchResults(
        name, template.getGroupId(), template.getArtifactId(), template.getVersion(), null, template.getClassNames()
      );
      //boolean tooManyResults = results.isTooManyResults();
      final SearchResults.Data data = results.getData();
      final ArrayList<RepositoryArtifactDescription> result = new ArrayList<>();
      if (data != null) {
        for (ArtifactType each : data.getArtifact()) {
          if (Comparing.equal(each.packaging, packaging)){
            result.add(convertArtifactInfo(each));
          }
        }
      }
      //if (tooManyResults) {
      //  result.add(null);
      //}
      return result;
    }
    catch (UnmarshalException e) {
      return Collections.emptyList();
    }
    catch (JAXBException e) {
      throw new IOException(e);
    }
  }
}
