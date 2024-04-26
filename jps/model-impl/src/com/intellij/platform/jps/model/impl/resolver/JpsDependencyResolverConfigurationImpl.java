// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.jps.model.impl.resolver;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.JpsElementChildRole;
import org.jetbrains.jps.model.ex.JpsElementBase;
import org.jetbrains.jps.model.ex.JpsElementChildRoleBase;
import com.intellij.platform.jps.model.resolver.JpsDependencyResolverConfiguration;

public class JpsDependencyResolverConfigurationImpl extends JpsElementBase<JpsDependencyResolverConfigurationImpl>
  implements JpsDependencyResolverConfiguration {
  public static final JpsElementChildRole<JpsDependencyResolverConfiguration> ROLE =
    JpsElementChildRoleBase.create("dependency resolver configuration");

  private boolean checksumVerificationEnabled;
  private boolean bindRepositoryEnabled;

  public JpsDependencyResolverConfigurationImpl() {
    this(false, false);
  }

  private JpsDependencyResolverConfigurationImpl(JpsDependencyResolverConfigurationImpl other) {
    this(other.checksumVerificationEnabled, other.bindRepositoryEnabled);
  }

  private JpsDependencyResolverConfigurationImpl(boolean checksumVerificationEnabled,
                                                 boolean bindRepositoryEnabled) {
    this.checksumVerificationEnabled = checksumVerificationEnabled;
    this.bindRepositoryEnabled = bindRepositoryEnabled;
  }

  @Override
  public boolean isSha256ChecksumVerificationEnabled() {
    return checksumVerificationEnabled;
  }

  @Override
  public boolean isBindRepositoryEnabled() {
    return bindRepositoryEnabled;
  }

  @Override
  public void setSha256ChecksumVerificationEnabled(boolean value) {
    checksumVerificationEnabled = value;
  }

  @Override
  public void setBindRepositoryEnabled(boolean value) {
    bindRepositoryEnabled = value;
  }

  @Override
  public @NotNull JpsDependencyResolverConfigurationImpl createCopy() {
    return new JpsDependencyResolverConfigurationImpl(this);
  }
}
