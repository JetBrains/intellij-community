/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import com.intellij.jarRepository.services.artifactory.ArtifactoryRepositoryService;
import com.intellij.jarRepository.services.nexus.NexusRepositoryService;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.util.ArrayUtil;
import com.intellij.util.SmartList;
import com.intellij.util.xmlb.annotations.AbstractCollection;
import com.intellij.util.xmlb.annotations.Property;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * @author Gregory.Shrago
 */
@State(
  name = "MavenServices",
  storages = @Storage("mavenServices.xml")
)
public class MavenRepositoryServicesManager implements PersistentStateComponent<MavenRepositoryServicesManager> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.repository.services.MavenRepositoryServicesManager");
  private final List<String> myUrls = new ArrayList<>();

  public static final List<String> DEFAULT_SERVICES = Collections.unmodifiableList(Arrays.asList(
    "https://oss.sonatype.org/service/local/",
    "http://repo.jfrog.org/artifactory/api/",
    "https://repository.jboss.org/nexus/service/local/"
  ));

  public MavenRepositoryServicesManager() {
    for (String s : DEFAULT_SERVICES) {
      myUrls.add(s);
    }
  }

  @NotNull
  public static MavenRepositoryServicesManager getInstance(Project project) {
    return ServiceManager.getService(project, MavenRepositoryServicesManager.class);
  }

  @NotNull
  public static MavenRepositoryService[] getServices() {
    return new MavenRepositoryService[]{new NexusRepositoryService(), new ArtifactoryRepositoryService()};
  }

  public static String[] getServiceUrls(final Project project) {
    return ArrayUtil.toStringArray(getInstance(project).getUrls());
  }

  @NotNull
  @Property(surroundWithTag = false)
  @AbstractCollection(surroundWithTag = false, elementTag = "service-url", elementValueAttribute = "")
  public List<String> getUrls() {
    return myUrls;
  }

  public void setUrls(@NotNull List<String> urls) {
    if (myUrls != urls) {
      myUrls.clear();
      myUrls.addAll(urls);
    }
  }

  @Override
  public MavenRepositoryServicesManager getState() {
    return this;
  }

  @Override
  public void loadState(MavenRepositoryServicesManager state) {
    myUrls.clear();
    myUrls.addAll(state.getUrls());
  }

  @NotNull
  public static List<RemoteRepositoryDescription> getRepositories(String url) {
    List<RemoteRepositoryDescription> result = new SmartList<>();
    for (MavenRepositoryService service : getServices()) {
      try {
        result.addAll(service.getRepositories(url));
      }
      catch (IOException e) {
        LOG.info(e);
      }
    }
    return result;
  }

  @NotNull
  public static List<RepositoryArtifactDescription> findArtifacts(@NotNull RepositoryArtifactDescription template, @NotNull String url) {
    final List<RepositoryArtifactDescription> result = new SmartList<>();
    for (MavenRepositoryService service : getServices()) {
      try {
        result.addAll(service.findArtifacts(url, template));
      }
      catch (IOException e) {
        LOG.info(e);
      }
    }
    return result;
  }
}
