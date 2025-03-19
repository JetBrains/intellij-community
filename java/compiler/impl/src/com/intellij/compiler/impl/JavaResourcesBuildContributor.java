// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compiler.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.builders.ModuleBasedBuildTargetType;
import org.jetbrains.jps.builders.java.ResourcesTargetType;

import java.util.List;

/**
 * @author Konstantin Aleev
 */
public final class JavaResourcesBuildContributor implements UpdateResourcesBuildContributor {
  @Override
  public @NotNull List<? extends ModuleBasedBuildTargetType<?>> getResourceTargetTypes() {
    return ResourcesTargetType.ALL_TYPES;
  }
}
