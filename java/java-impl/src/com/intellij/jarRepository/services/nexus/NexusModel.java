// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.jarRepository.services.nexus;

public class NexusModel {

  public static class RepositorySearchResults{
    public RepositoryType[] data;
  }
  public static class RepositoryType {
    public String id;
    public String name;
    public String provider;
    public String format;
    public String exposed;
    public String contentResourceURI;
  }

  public static class ArtifactSearchResults {

    public long totalCount;
    public long from;
    public int count;
    public ArtifactType[] data;
  }

  public static class ArtifactType {

    public String resourceUri;
    public String groupId;
    public String artifactId;
    public String version;
    public String classifier;
    public String packaging;
    public String extension;
    public String repoId;
    public String pomLink;
    public String artifactLink;
  }
}
