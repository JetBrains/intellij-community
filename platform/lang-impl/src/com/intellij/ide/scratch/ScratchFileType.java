// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.scratch;

import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.openapi.fileTypes.PlainTextLanguage;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * NOT USED.
 *
 * @author gregsh
 *
 * @deprecated use {@link ScratchFileService#findRootType(VirtualFile)} or {@link ScratchUtil#isScratch(VirtualFile)}.
 */
@Deprecated
@ApiStatus.ScheduledForRemoval(inVersion = "2020.3")
public class ScratchFileType extends LanguageFileType {

  /** @deprecated use {@link ScratchFileService#findRootType(VirtualFile)} or {@link ScratchUtil#isScratch(VirtualFile)}. */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2020.3")
  public static final LanguageFileType INSTANCE = new ScratchFileType();

  private ScratchFileType() {
    super(PlainTextLanguage.INSTANCE, true);
  }

  @NotNull
  @Override
  public String getName() {
    return "Scratch";
  }

  @NotNull
  @Override
  public String getDescription() {
    return "";
  }

  @NotNull
  @Override
  public String getDefaultExtension() {
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
