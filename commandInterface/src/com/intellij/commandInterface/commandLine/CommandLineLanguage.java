// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.commandInterface.commandLine;

import com.intellij.lang.Language;
import com.intellij.openapi.fileTypes.LanguageFileType;
import org.jetbrains.annotations.NotNull;

/**
 * Command line language itself
 * <pre>my_command --option=value foo</pre>
 * @author Ilya.Kazakevich
 */
public final class CommandLineLanguage extends Language {
  public static final CommandLineLanguage INSTANCE = new CommandLineLanguage();

  private CommandLineLanguage() {
    super("CommandLine");
  }

  @NotNull
  @Override
  public LanguageFileType getAssociatedFileType() {
    return CommandLineFileType.INSTANCE;
  }
}
