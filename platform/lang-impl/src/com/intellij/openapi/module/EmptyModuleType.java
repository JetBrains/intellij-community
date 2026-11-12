// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.module;

import com.intellij.icons.AllIcons;
import com.intellij.ide.util.projectWizard.EmptyModuleBuilder;
import com.intellij.openapi.project.ProjectBundle;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.Icon;

public final class EmptyModuleType extends ModuleType<EmptyModuleBuilder> {
  public static final @NonNls String EMPTY_MODULE = "EMPTY_MODULE";
  //private static final EmptyModuleType ourInstance = new EmptyModuleType();

  /**
   * @deprecated A call of this method initializes this class before {@link ModuleType}.
   * The static initializer of {@link ModuleType} creates an instance of this class.
   * So a first call of this method can deadlock with a thread that initializes {@link ModuleType}.
   * Use {@link ModuleType#EMPTY} to get the instance, and {@link #EMPTY_MODULE} to get the ID.
   */
  @Deprecated(forRemoval = true)
  public static EmptyModuleType getInstance() {
    return (EmptyModuleType)ModuleType.EMPTY;
  }

  /**
   * @deprecated Only {@link ModuleType} creates the instance.
   * A call of this constructor initializes this class before {@link ModuleType}, and that can deadlock.
   * Use {@link ModuleType#EMPTY}. The constructor will become private.
   */
  @Deprecated(forRemoval = true)
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
