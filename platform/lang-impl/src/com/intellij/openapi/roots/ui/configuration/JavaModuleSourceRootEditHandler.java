// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.ui.configuration;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.CustomShortcutSet;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.ui.DarculaColors;
import com.intellij.ui.JBColor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.java.JavaSourceRootType;

import javax.swing.*;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

public final class JavaModuleSourceRootEditHandler extends JavaSourceRootEditHandlerBase {
  private static final Color SOURCES_COLOR = new JBColor(new Color(0x0A50A1), DarculaColors.BLUE);

  public JavaModuleSourceRootEditHandler() {
    super(JavaSourceRootType.SOURCE);
  }

  @Override
  public @NotNull String getRootTypeName() {
    return ProjectBundle.message("module.toggle.sources.action");
  }

  @Override
  public @NotNull String getRootsGroupTitle() {
    return ProjectBundle.message("module.paths.sources.group");
  }

  @Override
  public @NotNull Icon getRootIcon() {
    return AllIcons.Modules.SourceRoot;
  }

  @Override
  protected @NotNull Icon getGeneratedRootIcon() {
    return AllIcons.Modules.GeneratedSourceRoot;
  }

  @Override
  public @Nullable Icon getFolderUnderRootIcon() {
    return AllIcons.Nodes.Package;
  }

  @Override
  public CustomShortcutSet getMarkRootShortcutSet() {
    return new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_S, InputEvent.ALT_MASK));
  }

  @Override
  public @NotNull Color getRootsGroupColor() {
    return SOURCES_COLOR;
  }

  @Override
  public @NotNull String getUnmarkRootButtonText() {
    return ProjectBundle.message("module.paths.unmark.source.tooltip");
  }
}
