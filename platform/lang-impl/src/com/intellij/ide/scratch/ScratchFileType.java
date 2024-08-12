// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.scratch;

import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.openapi.fileTypes.PlainTextLanguage;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * NOT USED.
 *
 * @author gregsh
 *
 * @deprecated use {@link ScratchFileService#findRootType(VirtualFile)} or {@link ScratchUtil#isScratch(VirtualFile)}.
 */
@Deprecated(forRemoval = true)
public class ScratchFileType extends LanguageFileType {

  /** @deprecated use {@link ScratchFileService#findRootType(VirtualFile)} or {@link ScratchUtil#isScratch(VirtualFile)}. */
  @Deprecated(forRemoval = true)
  public static final LanguageFileType INSTANCE = new ScratchFileType();

  private ScratchFileType() {
    super(PlainTextLanguage.INSTANCE, true);
  }

  @Override
  public @NotNull String getName() {
    return "Scratch";
  }

  @Override
  public @NotNull String getDescription() {
    return "";
  }

  @Override
  public @NotNull String getDefaultExtension() {
    return "";
  }

  @Override
  public Icon getIcon() {
    return PlainTextFileType.INSTANCE.getIcon();
  }

  @Override
  public boolean isReadOnly() {
    return true;
  }
}
