/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.jps.model.java.impl;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.java.JavaModuleIndex;
import org.jetbrains.jps.model.java.JavaSourceRootType;
import org.jetbrains.jps.model.java.compiler.JpsCompilerExcludes;
import org.jetbrains.jps.model.module.JpsModule;
import org.jetbrains.jps.model.module.JpsModuleSourceRoot;
import org.jetbrains.jps.util.JpsPathUtil;

import java.io.*;
import java.util.Collections;
import java.util.Map;
import java.util.Properties;

public class JavaModuleIndexImpl extends JavaModuleIndex {
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

    File index = new File(storageRoot, INDEX_PATH);
    FileUtil.createParentDirs(index);

    try (Writer writer = new OutputStreamWriter(new FileOutputStream(index), CharsetToolkit.UTF8_CHARSET)) {
      p.store(writer, null);
    }
  }

  public static JavaModuleIndex load(@NotNull File storageRoot, @NotNull JpsCompilerExcludes excludes) {
    File index = new File(storageRoot, INDEX_PATH);
    if (!index.exists()) {
      return new JavaModuleIndexImpl(excludes);
    }

    Properties p = new Properties();
    try (Reader reader = new InputStreamReader(new FileInputStream(index), CharsetToolkit.UTF8_CHARSET)) {
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
}