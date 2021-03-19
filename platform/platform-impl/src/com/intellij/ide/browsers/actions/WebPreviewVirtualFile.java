// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.browsers.actions;

import com.intellij.openapi.fileEditor.impl.NotSuitableForPreviewTab;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.util.Url;
import org.jetbrains.annotations.NotNull;

/**
 * @author Konstantin Bulenkov
 */
public class WebPreviewVirtualFile extends LightVirtualFile implements NotSuitableForPreviewTab {
  private final VirtualFile myFile;
  private final Url myPreviewUrl;

  public WebPreviewVirtualFile(VirtualFile file, Url previewUrl) {
    myFile = file;
    myPreviewUrl = previewUrl;
    setFileType(WebPreviewFileType.INSTANCE);
    setWritable(false);
  }

  @Override
  public VirtualFile getOriginalFile() {
    return myFile;
  }

  @Override
  public @NlsSafe @NotNull String getName() {
    return "Preview of " + myFile.getName();
  }

  public Url getPreviewUrl() {
    return myPreviewUrl;
  }
}
