/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.openapi.actionSystem.CustomShortcutSet;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.roots.SourceFolder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.JpsElement;
import org.jetbrains.jps.model.module.JpsModuleSourceRootType;

import javax.swing.*;
import java.awt.*;

/**
 * @author nik
 */
public abstract class ModuleSourceRootEditHandler<P extends JpsElement> {
  public static final ExtensionPointName<ModuleSourceRootEditHandler> EP_NAME = ExtensionPointName.create("com.intellij.projectStructure.sourceRootEditHandler");
  private final JpsModuleSourceRootType<P> myRootType;

  protected ModuleSourceRootEditHandler(JpsModuleSourceRootType<P> rootType) {
    myRootType = rootType;
  }

  @Nullable
  public static <P extends JpsElement> ModuleSourceRootEditHandler<P> getEditHandler(@NotNull JpsModuleSourceRootType<P> type) {
    for (ModuleSourceRootEditHandler editor : EP_NAME.getExtensions()) {
      if (editor.getRootType().equals(type)) {
        //noinspection unchecked
        return editor;
      }
    }
    return null;
  }

  public final JpsModuleSourceRootType<P> getRootType() {
    return myRootType;
  }

  @NotNull
  public abstract String getRootTypeName();

  @NotNull
  public String getFullRootTypeName() {
    return ProjectBundle.message("module.paths.root.node", getRootTypeName());
  }

  @NotNull
  public abstract Icon getRootIcon();

  @NotNull
  public Icon getRootIcon(@NotNull P properties) {
    return getRootIcon();
  }

  @Nullable
  public abstract Icon getFolderUnderRootIcon();

  @Nullable
  public abstract CustomShortcutSet getMarkRootShortcutSet();

  @NotNull
  public abstract String getRootsGroupTitle();

  @NotNull
  public abstract Color getRootsGroupColor();


  @NotNull
  public String getMarkRootButtonText() {
    return getRootTypeName();
  }

  @NotNull
  public abstract String getUnmarkRootButtonText();

  @Nullable
  public String getPropertiesString(@NotNull P properties) {
    return null;
  }

  @Nullable
  public JComponent createPropertiesEditor(@NotNull SourceFolder folder, @NotNull JComponent parentComponent,
                                           @NotNull ContentRootPanel.ActionCallback callback) {
    return null;
  }
}
