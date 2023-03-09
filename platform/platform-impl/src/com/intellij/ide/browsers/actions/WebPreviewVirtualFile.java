// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.browsers.actions;

import com.intellij.openapi.fileEditor.impl.FileEditorManagerImpl;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.util.Url;
import org.jetbrains.annotations.NotNull;

/**
 * @author Konstantin Bulenkov
 */
public class WebPreviewVirtualFile extends LightVirtualFile {
  private final VirtualFile myFile;
  private final Url myPreviewUrl;

  public WebPreviewVirtualFile(VirtualFile file, Url previewUrl) {
    myFile = file;
    myPreviewUrl = previewUrl;
    setFileType(WebPreviewFileType.INSTANCE);
    setWritable(false);
    putUserData(FileEditorManagerImpl.FORBID_PREVIEW_TAB, true);
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

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    WebPreviewVirtualFile file = (WebPreviewVirtualFile)o;

    if (!myFile.equals(file.myFile)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    return myFile.hashCode() * 31 + 1;
  }
}
