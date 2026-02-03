// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import java.util.Set;

public final class JpsModuleSourceRootImpl<P extends JpsElement> extends JpsCompositeElementBase<JpsModuleSourceRootImpl<P>> implements JpsTypedModuleSourceRoot<P> {
  private final JpsModuleSourceRootType<P> rootType;
  private final String url;

  public JpsModuleSourceRootImpl(String url, JpsModuleSourceRootType<P> type, P properties) {
    super();
    rootType = type;
    myContainer.setChild(type.getPropertiesRole(), properties);
    this.url = url;
  }

  @SuppressWarnings("deprecation")
  private JpsModuleSourceRootImpl(JpsModuleSourceRootImpl<P> original) {
    super(original);
    rootType = original.rootType;
    url = original.url;
  }

  @Override
  public <T extends JpsElement> T getProperties(@NotNull JpsModuleSourceRootType<T> type) {
    if (rootType.equals(type)) {
      //noinspection unchecked
      return (T)myContainer.getChild(rootType.getPropertiesRole());
    }
    return null;
  }

  @Override
  public @Nullable <T extends JpsElement> T getProperties(@NotNull Set<? extends JpsModuleSourceRootType<T>> types) {
    if (types.contains(rootType)) {
      //noinspection unchecked
      return (T)getProperties();
    }
    return null;
  }

  @Override
  public @Nullable <T extends JpsElement> JpsTypedModuleSourceRoot<T> asTyped(@NotNull JpsModuleSourceRootType<T> type) {
    //noinspection unchecked
    return rootType.equals(type) ? (JpsTypedModuleSourceRoot<T>)this : null;
  }

  @Override
  public @NotNull JpsTypedModuleSourceRoot<?> asTyped() {
    return this;
  }

  @Override
  public JpsElementType<?> getType() {
    return rootType;
  }

  @Override
  public @NotNull P getProperties() {
    return myContainer.getChild(rootType.getPropertiesRole());
  }

  @Override
  public @NotNull JpsModuleSourceRootType<P> getRootType() {
    return rootType;
  }

  @Override
  public @NotNull String getUrl() {
    return url;
  }

  @Override
  public @NotNull File getFile() {
    return JpsPathUtil.urlToFile(url);
  }

  @Override
  public @NotNull Path getPath() {
    return Path.of(JpsPathUtil.urlToPath(url));
  }

  @SuppressWarnings("removal")
  @Override
  public @NotNull JpsModuleSourceRootImpl<P> createCopy() {
    return new JpsModuleSourceRootImpl<>(this);
  }

  @Override
  public String toString() {
    return "JpsModuleSourceRootImpl(" +
           "rootType=" + rootType +
           ", url='" + url + '\'' +
           ')';
  }
}
