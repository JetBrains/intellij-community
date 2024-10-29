// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.ui.configuration;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.CustomShortcutSet;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.ui.JBColor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.java.JavaSourceRootType;

import javax.swing.*;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

public final class JavaTestSourceRootEditHandler extends JavaSourceRootEditHandlerBase {
  private static final Color TESTS_COLOR = new JBColor(new Color(0x008C2E), new Color(73, 140, 101));

  public JavaTestSourceRootEditHandler() {
    super(JavaSourceRootType.TEST_SOURCE);
  }

  @Override
  public @NotNull String getRootTypeName() {
    return ProjectBundle.message("module.toggle.test.sources.action");
  }

  @Override
  public @NotNull String getRootsGroupTitle() {
    return ProjectBundle.message("module.paths.test.sources.group");
  }

  @Override
  public @NotNull Icon getRootIcon() {
    return AllIcons.Modules.TestRoot;
  }

  @Override
  protected @NotNull Icon getGeneratedRootIcon() {
    return AllIcons.Modules.GeneratedTestRoot;
  }

  @Override
  public @Nullable Icon getFolderUnderRootIcon() {
    return AllIcons.Nodes.Package;
  }

  @Override
  public CustomShortcutSet getMarkRootShortcutSet() {
    return new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_T, InputEvent.ALT_MASK));
  }

  @Override
  public @NotNull Color getRootsGroupColor() {
    return TESTS_COLOR;
  }

  @Override
  public @NotNull String getMarkRootButtonText() {
    return ProjectBundle.message("button.folder.type.tests");
  }

  @Override
  public @NotNull String getUnmarkRootButtonText() {
    return ProjectBundle.message("module.paths.unmark.tests.tooltip");
  }
}
