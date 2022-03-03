// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.lang;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Path;
import java.util.function.Function;

@ApiStatus.Internal
public final class PathClassLoader extends UrlClassLoader {
  private static final Function<Path, ResourceFile> RESOURCE_FILE_FACTORY = file -> new ZipResourceFile(file);

  private static final boolean isParallelCapable = registerAsParallelCapable();

  private BytecodeTransformer transformer;

  public PathClassLoader(@NotNull UrlClassLoader.Builder builder) {
    super(builder, RESOURCE_FILE_FACTORY, isParallelCapable);
  }

  public interface BytecodeTransformer {
    default boolean isApplicable(String className, ClassLoader loader) {
      return true;
    }

    byte[] transform(ClassLoader loader, String className, byte[] classBytes);
  }

  public static Function<Path, ResourceFile> getResourceFileFactory() {
    return RESOURCE_FILE_FACTORY;
  }

  public void setTransformer(BytecodeTransformer transformer) {
    // redefinition is not allowed
    assert this.transformer == null;
    this.transformer = transformer;
  }

  // for java.system.class.loader
  @SuppressWarnings("unused")
  public PathClassLoader(@NotNull ClassLoader parent) {
    super(createDefaultBuilderForJdk(parent), RESOURCE_FILE_FACTORY, isParallelCapable);

    transformer = null;
    registerInClassLoaderValueMap(parent, this);
  }

  @Override
  public boolean isByteBufferSupported(@NotNull String name) {
    return transformer == null || !transformer.isApplicable(name, this);
  }

  @Override
  protected boolean isPackageDefined(String packageName) {
    return getDefinedPackage(packageName) != null;
  }

  @Override
  public Class<?> consumeClassData(@NotNull String name, byte[] data, Loader loader)
    throws IOException {
    BytecodeTransformer transformer = this.transformer;
    if (transformer != null && transformer.isApplicable(name, this)) {
      byte[] transformedData = transformer.transform(this, name, data);
      if (transformedData != null) {
        return super.consumeClassData(name, transformedData, loader);
      }
    }
    return super.consumeClassData(name, data, loader);
  }
}
