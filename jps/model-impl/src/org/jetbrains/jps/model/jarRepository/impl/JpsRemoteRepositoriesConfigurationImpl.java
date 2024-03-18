// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.model.jarRepository.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.JpsElementChildRole;
import org.jetbrains.jps.model.ex.JpsElementBase;
import org.jetbrains.jps.model.ex.JpsElementChildRoleBase;
import org.jetbrains.jps.model.jarRepository.JpsRemoteRepositoriesConfiguration;
import org.jetbrains.jps.model.jarRepository.JpsRemoteRepositoryDescription;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * @author Eugene Zhuravlev
 */
public final class JpsRemoteRepositoriesConfigurationImpl extends JpsElementBase<JpsRemoteRepositoriesConfigurationImpl> implements JpsRemoteRepositoriesConfiguration{
  public static final JpsElementChildRole<JpsRemoteRepositoriesConfiguration> ROLE = JpsElementChildRoleBase.create("remote repositories configuration");
  
  private final List<JpsRemoteRepositoryDescription> repositories = new ArrayList<>();

  public JpsRemoteRepositoriesConfigurationImpl() {
    this(Arrays.asList( // defaults
      new JpsRemoteRepositoryDescription("central", "Maven Central repository", "https://repo1.maven.org/maven2"),
      new JpsRemoteRepositoryDescription("jboss.community", "JBoss Community repository", "https://repository.jboss.org/nexus/content/repositories/public/")
    ));
  }

  public JpsRemoteRepositoriesConfigurationImpl(List<? extends JpsRemoteRepositoryDescription> repositories) {
    this.repositories.addAll(repositories);
  }

  @Override
  public @NotNull JpsRemoteRepositoriesConfigurationImpl createCopy() {
    return new JpsRemoteRepositoriesConfigurationImpl(repositories);
  }

  @Override
  public List<JpsRemoteRepositoryDescription> getRepositories() {
    return Collections.unmodifiableList(repositories);
  }

  @Override
  public void setRepositories(List<? extends JpsRemoteRepositoryDescription> repositories) {
    this.repositories.clear();
    this.repositories.addAll(repositories);
  }
}
