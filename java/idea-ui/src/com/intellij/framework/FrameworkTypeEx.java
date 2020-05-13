// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.framework;

import com.intellij.framework.addSupport.FrameworkSupportInModuleProvider;
import com.intellij.ide.util.frameworkSupport.FrameworkRole;
import com.intellij.ide.util.frameworkSupport.FrameworkSupportUtil;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.PossiblyDumbAware;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

public abstract class FrameworkTypeEx extends FrameworkType implements PossiblyDumbAware {
  public static final ExtensionPointName<FrameworkTypeEx> EP_NAME = ExtensionPointName.create("com.intellij.framework.type");

  protected FrameworkTypeEx(@NotNull String id) {
    super(id);
  }

  @Override
  public boolean isDumbAware() {
    return true;
  }

  /**
   * Puts it under another framework.
   * @see #getUnderlyingFrameworkTypeId()
   */
  @Nullable
  @Contract(pure = true)
  public FrameworkGroup<?> getParentGroup() {
    return null;
  }

  /**
   * Puts it under framework group.
   * @see #getParentGroup()
   */
  @Nullable
  @Contract(pure = true)
  public String getUnderlyingFrameworkTypeId() {
    return null;
  }

  @NotNull
  @Contract(pure = true)
  public abstract FrameworkSupportInModuleProvider createProvider();

  @Contract(pure = true)
  public <V extends FrameworkVersion> List<V> getVersions() {
    return Collections.emptyList();
  }

  public FrameworkRole[] getRoles() {
    FrameworkGroup<?> parentGroup = getParentGroup();
    if (parentGroup == null) {
      String id = getUnderlyingFrameworkTypeId();
      if (id != null) {
        FrameworkSupportInModuleProvider provider = FrameworkSupportUtil.findProvider(id);
        if (provider != null) return provider.getRoles();
      }
      return FrameworkRole.UNKNOWN;
    }
    else {
      FrameworkRole role = parentGroup.getRole();
      return null == role ? FrameworkRole.UNKNOWN : new FrameworkRole[]{role};
    }
  }
}
