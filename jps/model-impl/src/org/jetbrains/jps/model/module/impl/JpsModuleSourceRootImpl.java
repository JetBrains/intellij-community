// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.model.module.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.JpsElement;
import org.jetbrains.jps.model.JpsElementType;
import org.jetbrains.jps.model.ex.JpsCompositeElementBase;
import org.jetbrains.jps.model.module.JpsModuleSourceRootType;
import org.jetbrains.jps.model.module.JpsTypedModuleSourceRoot;
import org.jetbrains.jps.util.JpsPathUtil;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;

public final class JpsModuleSourceRootImpl<P extends JpsElement> extends JpsCompositeElementBase<JpsModuleSourceRootImpl<P>> implements JpsTypedModuleSourceRoot<P> {
  private final JpsModuleSourceRootType<P> myRootType;
  private final String myUrl;

  public JpsModuleSourceRootImpl(String url, JpsModuleSourceRootType<P> type, P properties) {
    super();
    myRootType = type;
    myContainer.setChild(type.getPropertiesRole(), properties);
    myUrl = url;
  }

  private JpsModuleSourceRootImpl(JpsModuleSourceRootImpl<P> original) {
    super(original);
    myRootType = original.myRootType;
    myUrl = original.myUrl;
  }

  @Override
  public <P extends JpsElement> P getProperties(@NotNull JpsModuleSourceRootType<P> type) {
    if (myRootType.equals(type)) {
      //noinspection unchecked
      return (P)myContainer.getChild(myRootType.getPropertiesRole());
    }
    return null;
  }

  @Override
  public @Nullable <P extends JpsElement> P getProperties(@NotNull Set<? extends JpsModuleSourceRootType<P>> types) {
    if (types.contains(myRootType)) {
      return (P)getProperties();
    }
    return null;
  }

  @Override
  public @Nullable <P extends JpsElement> JpsTypedModuleSourceRoot<P> asTyped(@NotNull JpsModuleSourceRootType<P> type) {
    //noinspection unchecked
    return myRootType.equals(type) ? (JpsTypedModuleSourceRoot<P>)this : null;
  }

  @Override
  public @NotNull JpsTypedModuleSourceRoot<?> asTyped() {
    return this;
  }

  @Override
  public JpsElementType<?> getType() {
    return myRootType;
  }

  @Override
  public @NotNull P getProperties() {
    return myContainer.getChild(myRootType.getPropertiesRole());
  }

  @Override
  public @NotNull JpsModuleSourceRootType<P> getRootType() {
    return myRootType;
  }

  @Override
  public @NotNull String getUrl() {
    return myUrl;
  }

  @Override
  public @NotNull File getFile() {
    return JpsPathUtil.urlToFile(myUrl);
  }

  @Override
  public @NotNull Path getPath() {
    return Paths.get(JpsPathUtil.urlToPath(myUrl));
  }

  @Override
  public @NotNull JpsModuleSourceRootImpl<P> createCopy() {
    return new JpsModuleSourceRootImpl<>(this);
  }
}
