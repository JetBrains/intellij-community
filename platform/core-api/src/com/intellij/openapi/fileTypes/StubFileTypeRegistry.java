// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileTypes;

import com.intellij.lang.Language;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * Supplies empty {@link FileTypeRegistry} implementation when {@link com.intellij.openapi.fileTypes.FileTypeManager} is not available
  */
final class StubFileTypeRegistry extends FileTypeRegistry {
  @Override
  public @NotNull FileType getFileTypeByFileName(@NotNull String fileName) {
    return StubLanguageFileType.INSTANCE;
  }

  @Override
  public @NotNull FileType getFileTypeByFile(@NotNull VirtualFile file) {
    return StubLanguageFileType.INSTANCE;
  }

  @Override
  public @NotNull FileType getFileTypeByExtension(@NotNull String extension) {
    return StubLanguageFileType.INSTANCE;
  }

  @Override
  public FileType @NotNull [] getRegisteredFileTypes() {
    return new FileType[] {StubLanguageFileType.INSTANCE};
  }

  @Override
  public boolean isFileIgnored(@NotNull VirtualFile file) {
    return false;
  }

  @Override
  public @Nullable FileType findFileTypeByName(@NotNull String fileTypeName) {
    return null;
  }

  private static final class StubLanguageFileType extends LanguageFileType {
    static final LanguageFileType INSTANCE = new StubLanguageFileType();

    private StubLanguageFileType() {
      super(Language.ANY);
    }

    @Override
    @NotNull
    public String getName() {
      return "Mock";
    }

    @Override
    @NotNull
    public String getDescription() {
      //noinspection HardCodedStringLiteral
      return "Mock";
    }

    @Override
    @NotNull
    public String getDefaultExtension() {
      return ".mockExtensionThatProbablyWon'tEverExist";
    }

    @Override
    public Icon getIcon() {
      return null;
    }
  }
}
