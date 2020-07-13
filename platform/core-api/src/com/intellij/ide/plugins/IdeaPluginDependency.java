// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins;

import com.intellij.openapi.extensions.PluginId;

public interface IdeaPluginDependency {
  PluginId getPluginId();
  boolean isOptional();
}
