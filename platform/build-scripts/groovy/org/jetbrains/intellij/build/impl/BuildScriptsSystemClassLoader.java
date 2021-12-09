// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.impl;

import com.intellij.util.lang.UrlClassLoader;
import org.jetbrains.annotations.NotNull;

/**
 * Use this classloader as system classloader when running build targets from IDE
 * This is required only for accommodating KotlinBinaries.ensureKotlinJpsPluginIsAddedToClassPath
 * and will be removed later, pinky promise.
 */
public class BuildScriptsSystemClassLoader extends UrlClassLoader {
  public BuildScriptsSystemClassLoader(@NotNull ClassLoader parent) {
    super(createDefaultBuilderForJdk(parent), null, false);
    registerInClassLoaderValueMap(parent, this);
  }
}
