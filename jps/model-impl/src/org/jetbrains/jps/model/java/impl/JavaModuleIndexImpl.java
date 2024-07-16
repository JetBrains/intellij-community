// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.model.java.impl;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.jps.model.ex.JpsElementBase;
import org.jetbrains.jps.model.java.JavaModuleIndex;
import org.jetbrains.jps.model.java.JavaSourceRootType;
import org.jetbrains.jps.model.java.compiler.JpsCompilerExcludes;
import org.jetbrains.jps.model.module.JpsModule;
import org.jetbrains.jps.util.JpsPathUtil;

import java.io.File;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@ApiStatus.Internal
public class JavaModuleIndexImpl extends JpsElementBase<JavaModuleIndexImpl> implements JavaModuleIndex {
  private static final String SOURCE_SUFFIX = ":S";
  private static final String TEST_SUFFIX = ":T";
  private static final String MODULE_INFO_FILE = "module-info.java";
  private static final File NULL_FILE = new File("-");

  private final Map<String, File> myMapping;
  private final JpsCompilerExcludes myExcludes;

  public JavaModuleIndexImpl(@NotNull JpsCompilerExcludes excludes) {
    myMapping = new ConcurrentHashMap<>();
    myExcludes = excludes;
  }

  @Override
  public @Nullable File getModuleInfoFile(@NotNull JpsModule module, boolean forTests) {
    var key = module.getName() + (forTests ? TEST_SUFFIX : SOURCE_SUFFIX);
    var file = myMapping.computeIfAbsent(key, __ -> findModuleInfoFile(module, forTests));
    return file == NULL_FILE ? null : file;
  }

  private File findModuleInfoFile(JpsModule module, boolean forTests) {
    var rootType = forTests ? JavaSourceRootType.TEST_SOURCE : JavaSourceRootType.SOURCE;

    for (var root : module.getSourceRoots()) {
      if (rootType.equals(root.getRootType())) {
        var file = new File(JpsPathUtil.urlToOsPath(root.getUrl()), MODULE_INFO_FILE);
        if (file.isFile() && !myExcludes.isExcluded(file)) {
          return file;
        }
      }
    }

    return NULL_FILE;
  }

  @TestOnly
  public void dropCache() {
    myMapping.clear();
  }
}
