// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.model.impl;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.JpsExcludePattern;
import org.jetbrains.jps.model.ex.JpsElementBase;

@ApiStatus.Internal
public final class JpsExcludePatternImpl extends JpsElementBase<JpsExcludePatternImpl> implements JpsExcludePattern {
  private final String myBaseDirUrl;
  private final String myPattern;

  public JpsExcludePatternImpl(@NotNull String baseDirUrl, @NotNull String pattern) {
    myBaseDirUrl = baseDirUrl;
    myPattern = pattern;
  }

  @Override
  public @NotNull String getBaseDirUrl() {
    return myBaseDirUrl;
  }

  @Override
  public @NotNull String getPattern() {
    return myPattern;
  }

  @Override
  public @NotNull JpsExcludePatternImpl createCopy() {
    return new JpsExcludePatternImpl(myBaseDirUrl, myPattern);
  }
}
