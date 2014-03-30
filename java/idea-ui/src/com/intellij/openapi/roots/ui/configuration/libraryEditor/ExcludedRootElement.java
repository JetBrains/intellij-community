/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.roots.ui.configuration.libraryEditor;

import com.intellij.icons.AllIcons;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFileManager;
import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public class ExcludedRootElement extends LibraryTableTreeContentElement<ExcludedRootElement> {
  @NotNull private final String myUrl;

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

  @NotNull
  public String getUrl() {
    return myUrl;
  }
}
