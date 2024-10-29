// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.module;

import com.intellij.icons.AllIcons;
import com.intellij.ide.util.projectWizard.EmptyModuleBuilder;
import com.intellij.openapi.project.ProjectBundle;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public final class EmptyModuleType extends ModuleType<EmptyModuleBuilder> {
  public static final @NonNls String EMPTY_MODULE = "EMPTY_MODULE";
  //private static final EmptyModuleType ourInstance = new EmptyModuleType();

  public static EmptyModuleType getInstance() {
    return (EmptyModuleType)ModuleType.EMPTY;
  }

  @SuppressWarnings({"UnusedDeclaration"}) // implicitly instantiated from ModuleType
  public EmptyModuleType() {
    this(EMPTY_MODULE);
  }

  private EmptyModuleType(@NonNls String id) {
    super(id);
  }

  @Override
  public @NotNull EmptyModuleBuilder createModuleBuilder() {
    return new EmptyModuleBuilder();
  }

  @Override
  public @NotNull String getName() {
    return ProjectBundle.message("module.type.empty.name");
  }

  @Override
  public @NotNull String getDescription() {
    return ProjectBundle.message("module.type.empty.description");
  }

  @Override
  public @NotNull Icon getNodeIcon(boolean isOpened) {
    return AllIcons.Nodes.Module;
  }
}
