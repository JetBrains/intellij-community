// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.jarRepository.services;

import com.intellij.jarRepository.RemoteRepositoryDescription;
import com.intellij.jarRepository.RepositoryArtifactDescription;
import com.intellij.jarRepository.services.artifactory.ArtifactoryRepositoryService;
import com.intellij.jarRepository.services.bintray.BintrayRepositoryService;
import com.intellij.jarRepository.services.nexus.NexusRepositoryService;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.SmartList;
import com.intellij.util.xmlb.annotations.Property;
import com.intellij.util.xmlb.annotations.XCollection;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Gregory.Shrago
 */
@State(
  name = "MavenServices",
  storages = @Storage("mavenServices.xml")
)
public class MavenRepositoryServicesManager implements PersistentStateComponent<MavenRepositoryServicesManager> {
  private static final Logger LOG = Logger.getInstance(MavenRepositoryServicesManager.class);
  private final List<String> myUrls = new ArrayList<>();

  public static final List<String> DEFAULT_SERVICES =
    List.of("https://oss.sonatype.org/service/local/", "https://repository.jboss.org/nexus/service/local/");

  public MavenRepositoryServicesManager() {
    myUrls.addAll(DEFAULT_SERVICES);
  }

  public static @NotNull MavenRepositoryServicesManager getInstance(Project project) {
    return project.getService(MavenRepositoryServicesManager.class);
  }

  public static MavenRepositoryService @NotNull [] getServices() {
    return new MavenRepositoryService[]{new NexusRepositoryService(), new ArtifactoryRepositoryService(), new BintrayRepositoryService()};
  }

  public static String[] getServiceUrls(final Project project) {
    return ArrayUtilRt.toStringArray(getInstance(project).getUrls());
  }

  @Property(surroundWithTag = false)
  @XCollection(elementName = "service-url", valueAttributeName = "")
  public @NotNull List<String> getUrls() {
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
  public void loadState(@NotNull MavenRepositoryServicesManager state) {
    myUrls.clear();
    myUrls.addAll(state.getUrls());
  }

  public static @NotNull List<RemoteRepositoryDescription> getRepositories(String url) {
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

  public static @NotNull List<RepositoryArtifactDescription> findArtifacts(@NotNull RepositoryArtifactDescription template, @NotNull String url) {
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
