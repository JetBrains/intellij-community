// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.commandInterface.commandLine;

import com.intellij.openapi.fileTypes.LanguageFileType;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * Command line file type
 * @author Ilya.Kazakevich
 */
public final class CommandLineFileType extends LanguageFileType {
  public static final CommandLineFileType INSTANCE = new CommandLineFileType();
  /**
   * Command line extension
   */
  @ApiStatus.Internal
  public static final String EXTENSION = "cmdline";

  private CommandLineFileType() {
    super(CommandLineLanguage.INSTANCE);
  }

  @Override
  public @NotNull String getName() {
    return CommandLineLanguage.INSTANCE.getID();
  }

  @Override
  public @NotNull String getDescription() {
    return CommandLineLanguage.INSTANCE.getID();
  }

  @Override
  public @NotNull String getDefaultExtension() {
    return EXTENSION;
  }

  @Override
  public Icon getIcon() {
    return null;
  }
}
