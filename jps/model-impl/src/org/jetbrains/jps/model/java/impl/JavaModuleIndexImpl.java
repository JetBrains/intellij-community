// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.model.java.impl;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.jps.model.ex.JpsElementBase;
import org.jetbrains.jps.model.java.JavaModuleIndex;
import org.jetbrains.jps.model.java.JavaSourceRootType;
import org.jetbrains.jps.model.java.compiler.JpsCompilerExcludes;
import org.jetbrains.jps.model.module.JpsModule;
import org.jetbrains.jps.model.module.JpsModuleSourceRoot;
import org.jetbrains.jps.util.JpsPathUtil;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;
import java.util.Properties;

public class JavaModuleIndexImpl extends JpsElementBase<JavaModuleIndexImpl> implements JavaModuleIndex {
  public static final String SOURCE_SUFFIX = ":S";
  public static final String TEST_SUFFIX = ":T";

  private static final String INDEX_PATH = "jigsaw/module-info.map";
  private static final String NULL_PATH = "-";
  private static final String MODULE_INFO_FILE = "module-info.java";

  private final Map<String, File> myMapping;
  private final JpsCompilerExcludes myExcludes;

  private JavaModuleIndexImpl(JpsCompilerExcludes excludes) {
    myMapping = ContainerUtil.newHashMap();
    myExcludes = excludes;
  }

  private JavaModuleIndexImpl(Map<String, File> mapping) {
    myMapping = Collections.unmodifiableMap(mapping);
    myExcludes = null;
  }

  @NotNull
  public JavaModuleIndexImpl createCopy() {
    final JpsCompilerExcludes excludes = myExcludes;
    if (excludes == null) {
      return new JavaModuleIndexImpl(myMapping);
    }
    final JavaModuleIndexImpl copy = new JavaModuleIndexImpl(excludes);
    copy.myMapping.putAll(myMapping);
    return copy;
  }

  public void applyChanges(@NotNull JavaModuleIndexImpl modified) {
    // not supported
  }

  @Nullable
  @Override
  public File getModuleInfoFile(@NotNull JpsModule module, boolean forTests) {
    String key = module.getName() + (forTests ? TEST_SUFFIX : SOURCE_SUFFIX);

    if (myExcludes == null || myMapping.containsKey(key)) {
      return myMapping.get(key);
    }

    File file = findModuleInfoFile(module, forTests ? JavaSourceRootType.TEST_SOURCE : JavaSourceRootType.SOURCE);
    myMapping.put(key, file);
    return file;
  }

  private File findModuleInfoFile(JpsModule module, JavaSourceRootType rootType) {
    for (JpsModuleSourceRoot root : module.getSourceRoots()) {
      if (rootType.equals(root.getRootType())) {
        File file = new File(JpsPathUtil.urlToOsPath(root.getUrl()), MODULE_INFO_FILE);
        if (file.isFile() && !myExcludes.isExcluded(file)) {
          return file;
        }
      }
    }

    return null;
  }

  public static void store(@NotNull File storageRoot, @NotNull Map<String, String> mapping) throws IOException {
    Properties p = new Properties();
    for (String key : mapping.keySet()) {
      String path = mapping.get(key);
      p.setProperty(key, path != null ? FileUtil.toSystemDependentName(path) : NULL_PATH);
    }

    Path index = new File(storageRoot, INDEX_PATH).toPath();
    Files.createDirectories(index.getParent());

    try (Writer writer = Files.newBufferedWriter(index, CharsetToolkit.UTF8_CHARSET)) {
      p.store(writer, null);
    }
  }

  public static JavaModuleIndex load(@NotNull File storageRoot, @NotNull JpsCompilerExcludes excludes) {
    Path index = new File(storageRoot, INDEX_PATH).toPath();
    if (!Files.exists(index)) {
      return new JavaModuleIndexImpl(excludes);
    }

    Properties p = new Properties();
    try (Reader reader = Files.newBufferedReader(index, CharsetToolkit.UTF8_CHARSET)) {
      p.load(reader);
    }
    catch (IOException e) {
      throw new RuntimeException("Failed to read module index file: " + index, e);
    }

    Map<String, File> mapping = ContainerUtil.newHashMap();
    for (String key : p.stringPropertyNames()) {
      String path = p.getProperty(key);
      mapping.put(key, NULL_PATH.equals(path) ? null : new File(path));
    }
    return new JavaModuleIndexImpl(mapping);
  }

  @TestOnly
  public void dropCache() {
    myMapping.clear();
  }
}