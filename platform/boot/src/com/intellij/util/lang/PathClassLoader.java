// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.lang;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Locale;
import java.util.function.Function;

/**
 * This classloader implementation is separate from {@link UrlClassLoader}
 * because {@link UrlClassLoader} is used in runtime modules with JDK 1.8,
 * and this one runs in the IDE process and uses JDK 11+ features.
 */
@ApiStatus.Internal
public final class PathClassLoader extends UrlClassLoader {
  static final Function<Path, ResourceFile> RESOURCE_FILE_FACTORY;

  static {
    boolean defineClassUsingBytes = Boolean.parseBoolean(System.getProperty("idea.define.class.using.byte.array", "false"));
    if (!defineClassUsingBytes && System.getProperty("os.name").toLowerCase(Locale.ENGLISH).startsWith("windows")) {
      RESOURCE_FILE_FACTORY = file -> {
        String path = file.toString();
        return new ZipResourceFile(file, path.length() > 2 && path.charAt(0) == '\\' && path.charAt(1) == '\\');
      };
    }
    else {
      RESOURCE_FILE_FACTORY = file -> {
        return new ZipResourceFile(file, defineClassUsingBytes);
      };
    }
  }

  private static final boolean isParallelCapable = ClassLoader.registerAsParallelCapable();

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

  @SuppressWarnings("unused")
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
    super(UrlClassLoader.createDefaultBuilderForJdk(parent), RESOURCE_FILE_FACTORY, isParallelCapable);

    transformer = null;
    UrlClassLoader.registerInClassLoaderValueMap(parent, this);
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
