// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.model.java.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.JpsElementCreator;
import org.jetbrains.jps.model.ex.JpsElementBase;
import org.jetbrains.jps.model.ex.JpsElementChildRoleBase;
import org.jetbrains.jps.model.java.ExplodedDirectoryModuleExtension;

import java.util.Objects;

class ExplodedDirectoryModuleExtensionImpl extends JpsElementBase<ExplodedDirectoryModuleExtensionImpl> implements
                                                                                                               ExplodedDirectoryModuleExtension {
  private String myExplodedUrl;
  private boolean myExcludeExploded;

  ExplodedDirectoryModuleExtensionImpl() {
  }

  ExplodedDirectoryModuleExtensionImpl(ExplodedDirectoryModuleExtensionImpl original) {
    myExcludeExploded = original.myExcludeExploded;
    myExplodedUrl = original.myExplodedUrl;
  }

  @Override
  public String getExplodedUrl() {
    return myExplodedUrl;
  }

  @Override
  public void setExplodedUrl(String explodedUrl) {
    if (!Objects.equals(myExplodedUrl, explodedUrl)) {
      myExplodedUrl = explodedUrl;
    }
  }

  @Override
  public boolean isExcludeExploded() {
    return myExcludeExploded;
  }

  @Override
  public void setExcludeExploded(boolean excludeExploded) {
    if (myExcludeExploded != excludeExploded) {
      myExcludeExploded = excludeExploded;
    }
  }

  @NotNull
  @Override
  public ExplodedDirectoryModuleExtensionImpl createCopy() {
    return new ExplodedDirectoryModuleExtensionImpl(this);
  }

  public static class ExplodedDirectoryModuleExtensionRole extends JpsElementChildRoleBase<ExplodedDirectoryModuleExtension> implements JpsElementCreator<ExplodedDirectoryModuleExtension> {
    public static final ExplodedDirectoryModuleExtensionRole INSTANCE = new ExplodedDirectoryModuleExtensionRole();

    public ExplodedDirectoryModuleExtensionRole() {
      super("exploded directory");
    }

    @NotNull
    @Override
    public ExplodedDirectoryModuleExtension create() {
      return new ExplodedDirectoryModuleExtensionImpl();
    }
  }
}
