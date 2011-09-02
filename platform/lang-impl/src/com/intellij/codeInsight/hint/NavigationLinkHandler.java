/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.codeInsight.hint;

import com.intellij.codeInsight.highlighting.TooltipLinkHandler;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;


/**
 * Handles tooltip links in format <code>#navigation/file_path:offset</code>.
 * On a click opens specified file in an editor and positions caret to the given offset.
 */
public class NavigationLinkHandler extends TooltipLinkHandler {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.hint.NavigationLinkHandler");

  @Override
  public boolean handleLink(@NotNull final String refSuffix, @NotNull final Editor editor) {
    final int pos = refSuffix.lastIndexOf(':');
    if (pos <= 0 || pos == refSuffix.length() - 1) {
      LOG.error("Malformed suffix: " + refSuffix);
      return true;
    }

    final String path = refSuffix.substring(0, pos);
    final VirtualFile vFile = LocalFileSystem.getInstance().findFileByPath(path);
    if (vFile == null) {
      LOG.error("Unknown file: " + path);
      return true;
    }

    final int offset;
    try {
      offset = Integer.parseInt(refSuffix.substring(pos + 1));
    }
    catch (NumberFormatException e) {
      LOG.error("Malformed suffix: " + refSuffix);
      return true;
    }

    new OpenFileDescriptor(editor.getProject(), vFile, offset).navigate(true);
    return true;
  }
}
