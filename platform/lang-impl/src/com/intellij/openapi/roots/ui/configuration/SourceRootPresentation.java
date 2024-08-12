// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.openapi.roots.ui.configuration;

import com.intellij.openapi.roots.SourceFolder;
import com.intellij.util.PlatformIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.JpsElement;
import org.jetbrains.jps.model.module.JpsModuleSourceRootType;
import org.jetbrains.jps.model.module.JpsTypedModuleSourceRoot;

import javax.swing.*;

/**
 * @author Eugene Zhuravlev
 * @author 2003
 */
public final class SourceRootPresentation {
  public static @NotNull Icon getSourceRootIcon(@NotNull SourceFolder sourceFolder) {
    return getSourceRootIcon(sourceFolder.getJpsElement().asTyped());
  }

  public static @Nullable Icon getSourceRootFileLayerIcon(@NotNull JpsModuleSourceRootType<?> rootType) {
    ModuleSourceRootEditHandler<?> handler = ModuleSourceRootEditHandler.getEditHandler(rootType);
    return handler != null ? handler.getRootFileLayerIcon() : null;
  }

  private static @NotNull <P extends JpsElement> Icon getSourceRootIcon(@NotNull JpsTypedModuleSourceRoot<P> root) {
    ModuleSourceRootEditHandler<P> handler = ModuleSourceRootEditHandler.getEditHandler(root.getRootType());
    return handler != null ? handler.getRootIcon(root.getProperties()) : PlatformIcons.FOLDER_ICON;
  }
}
