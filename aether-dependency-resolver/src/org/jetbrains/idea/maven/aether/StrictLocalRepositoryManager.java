// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.aether;

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.metadata.Metadata;
import org.eclipse.aether.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.zip.ZipFile;

class StrictLocalRepositoryManager implements LocalRepositoryManager {
  private static final Logger LOG = LoggerFactory.getLogger(StrictLocalRepositoryManager.class);
  private static final boolean STRICT_VALIDATION = "true".equals(System.getProperty("org.jetbrains.idea.maven.aether.strictValidation"));
  private final LocalRepositoryManager delegate;

  StrictLocalRepositoryManager(LocalRepositoryManager delegate) {
    this.delegate = delegate;
  }

  @Override
  public LocalArtifactResult find(RepositorySystemSession session, LocalArtifactRequest request) {
    var result = delegate.find(session, request);
    if (result.isAvailable() && !isValidArchive(result.getFile())) {
      result.setFile(null);
      result.setAvailable(false);
    }
    return result;
  }

  boolean isValidArchive(File archive) {
    if (!archive.exists()) return false;
    // TODO: to be revised after IDEA-269182 is implemented
    if (STRICT_VALIDATION && (archive.getName().endsWith(".jar") || archive.getName().endsWith(".zip"))) {
      long entriesCount;
      try (var zip = new ZipFile(archive)) {
        entriesCount = zip.size();
      }
      catch (IOException e) {
        LOG.warn("Unable to read a number of entries in " + archive, e);
        entriesCount = 0;
      }
      if (entriesCount <= 0) {
        LOG.warn(archive + " is probably corrupted, deleting");
        try {
          Files.deleteIfExists(archive.toPath());
        }
        catch (IOException e) {
          throw new RuntimeException("Unable to delete " + archive, e);
        }
        return false;
      }
    }
    return true;
  }

  @Override
  public LocalRepository getRepository() {
    return delegate.getRepository();
  }

  @Override
  public String getPathForLocalArtifact(Artifact artifact) {
    return delegate.getPathForLocalArtifact(artifact);
  }

  @Override
  public String getPathForRemoteArtifact(Artifact artifact, RemoteRepository repository, String context) {
    return delegate.getPathForRemoteArtifact(artifact, repository, context);
  }

  @Override
  public String getPathForLocalMetadata(Metadata metadata) {
    return delegate.getPathForLocalMetadata(metadata);
  }

  @Override
  public String getPathForRemoteMetadata(Metadata metadata, RemoteRepository repository, String context) {
    return delegate.getPathForRemoteMetadata(metadata, repository, context);
  }

  @Override
  public void add(RepositorySystemSession session, LocalArtifactRegistration request) {
    delegate.add(session, request);
  }

  @Override
  public LocalMetadataResult find(RepositorySystemSession session, LocalMetadataRequest request) {
    return delegate.find(session, request);
  }

  @Override
  public void add(RepositorySystemSession session, LocalMetadataRegistration request) {
    delegate.add(session, request);
  }
}
