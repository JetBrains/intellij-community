// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.lang;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;
import java.security.ProtectionDomain;
import java.util.function.Function;

@ApiStatus.Internal
public final class PathClassLoader extends UrlClassLoader {
  private static final Function<Path, ResourceFile> RESOURCE_FILE_FACTORY = file -> new ZipResourceFile(file);

  private static final boolean isParallelCapable = registerAsParallelCapable();
  private static final ClassLoader appClassLoader = PathClassLoader.class.getClassLoader();

  private final BytecodeTransformer transformer;

  public PathClassLoader(@NotNull UrlClassLoader.Builder builder) {
    super(builder, RESOURCE_FILE_FACTORY, isParallelCapable);

    transformer = null;
  }

  public interface BytecodeTransformer {
    default boolean isApplicable(String className, ClassLoader loader, @Nullable ProtectionDomain protectionDomain) {
      return true;
    }

    byte[] transform(ClassLoader loader, String className, @Nullable ProtectionDomain protectionDomain, byte[] classBytes);
  }

  @SuppressWarnings("unused")
  public static Function<Path, ResourceFile> getResourceFileFactory() {
    return RESOURCE_FILE_FACTORY;
  }

  public PathClassLoader(Builder builder, BytecodeTransformer transformer) {
    super(builder, RESOURCE_FILE_FACTORY, isParallelCapable);

    this.transformer = transformer;
  }

  // for java.system.class.loader
  @SuppressWarnings("unused")
  public PathClassLoader(@NotNull ClassLoader parent) {
    super(createDefaultBuilderForJdk(parent), RESOURCE_FILE_FACTORY, isParallelCapable);

    transformer = null;
    registerInClassLoaderValueMap(parent, this);

    // who knows
    assert appClassLoader != this;
  }

  @Override
  protected Class<?> findClass(@NotNull String name) throws ClassNotFoundException {
    if (name.startsWith("com.intellij.util.lang.")) {
      return appClassLoader.loadClass(name);
    }
    else {
      return super.findClass(name);
    }
  }

  @Override
  public boolean isByteBufferSupported(@NotNull String name, @Nullable ProtectionDomain protectionDomain) {
    return transformer == null || !transformer.isApplicable(name, this, protectionDomain);
  }

  @Override
  protected boolean isPackageDefined(String packageName) {
    return getDefinedPackage(packageName) != null;
  }

  @Override
  public Class<?> consumeClassData(@NotNull String name, byte[] data, Loader loader, @Nullable ProtectionDomain protectionDomain)
    throws IOException {
    if (transformer != null && transformer.isApplicable(name, this, protectionDomain)) {
      byte[] transformedData = transformer.transform(this, name, protectionDomain, data);
      if (transformedData != null) {
        return super.consumeClassData(name, transformedData, loader, protectionDomain);
      }
    }
    return super.consumeClassData(name, data, loader, protectionDomain);
  }
}
