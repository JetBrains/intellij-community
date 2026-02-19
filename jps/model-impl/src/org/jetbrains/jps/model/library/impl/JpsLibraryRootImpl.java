// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.model.library.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.ex.JpsElementBase;
import org.jetbrains.jps.model.library.JpsLibrary;
import org.jetbrains.jps.model.library.JpsLibraryRoot;
import org.jetbrains.jps.model.library.JpsOrderRootType;

final class JpsLibraryRootImpl extends JpsElementBase<JpsLibraryRootImpl> implements JpsLibraryRoot {
  private final String myUrl;
  private final JpsOrderRootType myRootType;
  private final InclusionOptions myOptions;

  JpsLibraryRootImpl(@NotNull String url, @NotNull JpsOrderRootType rootType, @NotNull InclusionOptions options) {
    myUrl = url;
    myRootType = rootType;
    myOptions = options;
  }

  JpsLibraryRootImpl(JpsLibraryRootImpl original) {
    myUrl = original.myUrl;
    myRootType = original.myRootType;
    myOptions = original.myOptions;
  }

  @Override
  public @NotNull JpsOrderRootType getRootType() {
    return myRootType;
  }

  @Override
  public @NotNull String getUrl() {
    return myUrl;
  }

  @Override
  public @NotNull InclusionOptions getInclusionOptions() {
    return myOptions;
  }

  @Override
  public @NotNull JpsLibraryRootImpl createCopy() {
    return new JpsLibraryRootImpl(this);
  }

  @Override
  public @NotNull JpsLibrary getLibrary() {
    return (JpsLibrary)myParent.getParent();
  }
}
