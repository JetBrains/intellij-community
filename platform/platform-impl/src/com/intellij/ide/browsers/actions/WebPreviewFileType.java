// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.browsers.actions;

import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.fileTypes.ex.FakeFileType;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author Konstantin Bulenkov
 */
public class WebPreviewFileType extends FakeFileType {
  public static final WebPreviewFileType INSTANCE = new WebPreviewFileType();

  private WebPreviewFileType() {
  }

  @Override
  public @NonNls @NotNull String getName() {
    return "WebPreview";
  }

  @Override
  public @NotNull @Nls String getDisplayName() {
    return IdeBundle.message("filetype.web.preview.display.name");
  }

  @Override
  public @NlsContexts.Label @NotNull String getDescription() {
    return IdeBundle.message("filetype.web.preview.description");
  }

  @Override
  public boolean isMyFileType(@NotNull VirtualFile file) {
    return file instanceof WebPreviewVirtualFile;
  }

  @Override
  public Icon getIcon() {
    return AllIcons.Nodes.PpWeb;
  }
}
