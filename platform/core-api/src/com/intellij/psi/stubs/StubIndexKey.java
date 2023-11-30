// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

/*
 * @author max
 */
package com.intellij.psi.stubs;

import com.intellij.ide.plugins.PluginUtil;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.psi.PsiElement;
import com.intellij.util.indexing.ID;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class StubIndexKey<K, Psi extends PsiElement> extends ID<K, Psi> {
  private StubIndexKey(@NonNls String name, @Nullable PluginId pluginId) {
    super(name, pluginId);
  }

  public static synchronized @NotNull <K, Psi extends PsiElement> StubIndexKey<K, Psi> createIndexKey(@NonNls @NotNull String name) {
    PluginId pluginId = PluginUtil.getInstance().getCallerPlugin(3);
    ID<?, ?> existing = findByName(name, true, pluginId);
    if (existing != null) {
      if (existing instanceof StubIndexKey) {
        return (StubIndexKey<K, Psi>) existing;
      }
      throw new IllegalStateException("key with id " + name + " is already registered", existing.getRegistrationTrace());
    }
    return new StubIndexKey<>(name, pluginId);
  }

}