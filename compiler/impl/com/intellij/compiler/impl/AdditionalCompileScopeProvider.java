/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.compiler.impl;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.compiler.CompileScope;
import org.jetbrains.annotations.Nullable;

/**
 * @author nik
 */
public abstract class AdditionalCompileScopeProvider {
  public static final ExtensionPointName<AdditionalCompileScopeProvider> EXTENSION_POINT_NAME = ExtensionPointName.create("com.intellij.compiler.additionalCompileScopeProvider");

  @Nullable
  public abstract CompileScope getAdditionalScope(CompileScope baseScope);
}
