/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.openapi.roots.ui.configuration;

import com.intellij.openapi.roots.SourceFolder;
import com.intellij.util.PlatformIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.JpsElement;
import org.jetbrains.jps.model.module.JpsTypedModuleSourceRoot;

import javax.swing.*;

/**
 * @author Eugene Zhuravlev
 * @author 2003
 */
public class SourceRootPresentation {
  @NotNull
  public static Icon getSourceRootIcon(@NotNull SourceFolder sourceFolder) {
    return getSourceRootIcon(sourceFolder.getJpsElement().asTyped());
  }

  @NotNull
  private static <P extends JpsElement> Icon getSourceRootIcon(@NotNull JpsTypedModuleSourceRoot<P> root) {
    ModuleSourceRootEditHandler<P> handler = ModuleSourceRootEditHandler.getEditHandler(root.getRootType());
    return handler != null ? handler.getRootIcon(root.getProperties()) : PlatformIcons.DIRECTORY_CLOSED_ICON;
  }
}
