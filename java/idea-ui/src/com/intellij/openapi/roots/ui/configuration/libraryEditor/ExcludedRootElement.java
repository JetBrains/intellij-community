// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.ui.configuration.libraryEditor;

import com.intellij.icons.AllIcons;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFileManager;
import org.jetbrains.annotations.NotNull;

public class ExcludedRootElement extends LibraryTableTreeContentElement<ExcludedRootElement> {
  private final @NotNull String myUrl;

  public ExcludedRootElement(@NotNull NodeDescriptor parentDescriptor, String rootUrl, @NotNull String excludedUrl) {
    super(parentDescriptor);
    myUrl = excludedUrl;
    if (excludedUrl.startsWith(rootUrl)) {
      String relativePath = StringUtil.trimStart(excludedUrl.substring(rootUrl.length()), "/");
      myName = relativePath.isEmpty() ? "<all>" : relativePath;
    }
    else {
      myName = ItemElement.getPresentablePath(excludedUrl);
    }
    myColor = getForegroundColor(VirtualFileManager.getInstance().findFileByUrl(excludedUrl) != null);
    setIcon(AllIcons.Modules.ExcludeRoot);
  }

  public @NotNull String getUrl() {
    return myUrl;
  }
}
