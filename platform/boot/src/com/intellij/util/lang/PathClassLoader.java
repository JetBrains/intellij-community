// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.lang;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

/**
 * This classloader implementation is separate from {@link UrlClassLoader}
 * because {@link UrlClassLoader} is used in runtime modules with JDK 1.8,
 * and this one runs in the IDE process and uses JDK 11+ features.
 */
@ApiStatus.Internal
public final class PathClassLoader extends UrlClassLoader {
  @VisibleForTesting
  public static final String RESET_CLASSPATH_FROM_MANIFEST_PROPERTY = "idea.reset.classpath.from.manifest";

  static final Function<Path, ResourceFile> RESOURCE_FILE_FACTORY;

  static {
    boolean defineClassUsingBytes = Boolean.parseBoolean(System.getProperty("idea.define.class.using.byte.array", "false"));
    ZipFilePool zipPool = ZipFilePool.PATH_CLASSLOADER_POOL;
    if (!defineClassUsingBytes && System.getProperty("os.name").toLowerCase(Locale.ENGLISH).startsWith("windows")) {
      RESOURCE_FILE_FACTORY = file -> {
        String path = file.toString();
        return new ZipResourceFile(file, path.length() > 2 && path.charAt(0) == '\\' && path.charAt(1) == '\\', zipPool);
      };
    }
    else {
      RESOURCE_FILE_FACTORY = file -> {
        return new ZipResourceFile(file, defineClassUsingBytes, zipPool);
      };
    }
  }

  private static final boolean isParallelCapable = ClassLoader.registerAsParallelCapable();

  private BytecodeTransformer transformer;

  public PathClassLoader(@NotNull UrlClassLoader.Builder builder) {
    super(builder, RESOURCE_FILE_FACTORY, isParallelCapable);

    parseManifestAndResetClassPath(classPath);
  }

  public void reset(Collection<Path> newClassPath) {
    classPath.reset(newClassPath);
  }

  public interface BytecodeTransformer {
    default boolean isApplicable(String className, ClassLoader loader) {
      return true;
    }

    byte @Nullable [] transform(ClassLoader loader, String className, byte[] classBytes);
  }

  @SuppressWarnings("unused") // accessed through reflection in ClassLoaderConfigurator
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
    parseManifestAndResetClassPath(classPath);
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
  public Class<?> consumeClassData(@NotNull String name, byte[] data)
    throws IOException {
    BytecodeTransformer transformer = this.transformer;
    if (transformer != null && transformer.isApplicable(name, this)) {
      byte[] transformedData = transformer.transform(this, name, data);
      if (transformedData != null) {
        return super.consumeClassData(name, transformedData);
      }
    }
    return super.consumeClassData(name, data);
  }

  @SuppressWarnings("IO_FILE_USAGE")
  private static void parseManifestAndResetClassPath(@NotNull ClassPath classPath) {
    String systemProp = System.getProperty(RESET_CLASSPATH_FROM_MANIFEST_PROPERTY);
    if (systemProp == null || classPath.getFiles().size() != 1) {
      return;
    }

    try {
      Path jarPath = classPath.getFiles().get(0);
      try (JarFile zipFile = new JarFile(jarPath.toFile())) {
        Manifest manifest = zipFile.getManifest();
        if (manifest == null) {
          return;
        }

        String classPathAttr = manifest.getMainAttributes().getValue(Attributes.Name.CLASS_PATH);
        if (classPathAttr == null) {
          return;
        }

        String[] paths = classPathAttr.split(" ");
        List<Path> newPaths = new ArrayList<>(paths.length);
        for (String path : paths) {
          if (!path.startsWith("file:")) {
            throw new IllegalArgumentException("Classpath entry must be a file: URL: " + path);
          }
          newPaths.add(Paths.get(urlToFilePath(path)));
        }
        classPath.reset(newPaths);
      }
    }
    catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
