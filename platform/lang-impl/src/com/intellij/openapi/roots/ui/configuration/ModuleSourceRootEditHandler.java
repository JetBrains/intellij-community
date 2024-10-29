// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.ui.configuration;

import com.intellij.openapi.actionSystem.CustomShortcutSet;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.roots.SourceFolder;
import com.intellij.openapi.util.NlsSafe;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.JpsElement;
import org.jetbrains.jps.model.module.JpsModuleSourceRootType;

import javax.swing.*;
import java.awt.*;

/**
 * Inherit from this class and register the implementation as {@code projectStructure.sourceRootEditHandler} extension in plugin.xml to
 * specify how source roots of a custom {@link JpsModuleSourceRootType type} should be shown in Project Structure dialog. There must be only
 * one instance of this class for each {@link JpsModuleSourceRootType}.
 */
public abstract class ModuleSourceRootEditHandler<P extends JpsElement> {
  public static final ExtensionPointName<ModuleSourceRootEditHandler> EP_NAME = ExtensionPointName.create("com.intellij.projectStructure.sourceRootEditHandler");
  private final JpsModuleSourceRootType<P> myRootType;

  protected ModuleSourceRootEditHandler(JpsModuleSourceRootType<P> rootType) {
    myRootType = rootType;
  }

  public static @Nullable <P extends JpsElement> ModuleSourceRootEditHandler<P> getEditHandler(@NotNull JpsModuleSourceRootType<P> type) {
    //noinspection unchecked
    return EP_NAME.getExtensionList().stream().filter(editor -> editor.getRootType().equals(type)).findFirst().orElse(null);
  }

  public final JpsModuleSourceRootType<P> getRootType() {
    return myRootType;
  }

  public abstract @NotNull @Nls(capitalization = Nls.Capitalization.Title) String getRootTypeName();

  public @NotNull @Nls String getFullRootTypeName() {
    return ProjectBundle.message("module.paths.root.node", getRootTypeName());
  }

  public abstract @NotNull Icon getRootIcon();

  public @NotNull Icon getRootIcon(@NotNull P properties) {
    return getRootIcon();
  }

  public @Nullable Icon getRootFileLayerIcon() {
    return null;
  }

  public abstract @Nullable Icon getFolderUnderRootIcon();

  public abstract @Nullable CustomShortcutSet getMarkRootShortcutSet();

  public abstract @NotNull @Nls(capitalization = Nls.Capitalization.Title) String getRootsGroupTitle();

  public abstract @NotNull Color getRootsGroupColor();


  public @NotNull @Nls(capitalization = Nls.Capitalization.Title) String getMarkRootButtonText() {
    return getRootTypeName();
  }

  public abstract @NotNull @Nls(capitalization = Nls.Capitalization.Title) String getUnmarkRootButtonText();

  public @Nullable @NlsSafe String getPropertiesString(@NotNull P properties) {
    return null;
  }

  public @Nullable JComponent createPropertiesEditor(@NotNull SourceFolder folder, @NotNull JComponent parentComponent,
                                                     @NotNull ContentRootPanel.ActionCallback callback) {
    return null;
  }
}
