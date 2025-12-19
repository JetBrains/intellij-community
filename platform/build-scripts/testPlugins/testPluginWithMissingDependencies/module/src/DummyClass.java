// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.buildScripts.testPlugins.testPluginWithMissingDependencies.module;

import com.intellij.jsonpath.JsonPathBundle;
import com.intellij.vcsUtil.VcsUtil;
import org.editorconfig.core.EditorConfigAutomatonBuilder;

/**
 * This is a dummy class that contains references to classes from the dependencies to ensure that they won't be removed as unused.
 */
@SuppressWarnings("unused")
final class DummyClass {
  static final Class<?>[] references = new Class<?>[]{
    VcsUtil.class,
    EditorConfigAutomatonBuilder.class,
    JsonPathBundle.class,
  };
}