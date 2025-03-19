// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.jps.model.resolver;

import org.jetbrains.jps.model.JpsElement;

public interface JpsDependencyResolverConfiguration extends JpsElement {
  boolean isSha256ChecksumVerificationEnabled();

  boolean isBindRepositoryEnabled();

  void setSha256ChecksumVerificationEnabled(boolean value);

  void setBindRepositoryEnabled(boolean value);
}
