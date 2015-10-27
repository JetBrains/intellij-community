/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.injected.editor;

import com.intellij.lang.Language;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.LightVirtualFile;
import org.jetbrains.annotations.NotNull;

/**
 * @author cdr
 */
public class VirtualFileWindowImpl extends LightVirtualFile implements VirtualFileWindow {
  private final VirtualFile myDelegate;
  private final DocumentWindowImpl myDocumentWindow;

  public VirtualFileWindowImpl(@NotNull String name, @NotNull VirtualFile delegate, @NotNull DocumentWindowImpl window, @NotNull Language language, @NotNull CharSequence text) {
    super(name, language, text);
    setCharset(delegate.getCharset());
    setFileType(language.getAssociatedFileType());

    myDelegate = delegate;
    myDocumentWindow = window;
  }

  @Override
  public VirtualFile getDelegate() {
    return myDelegate;
  }

  @Override
  public DocumentWindowImpl getDocumentWindow() {
    return myDocumentWindow;
  }

  @Override
  public boolean isValid() {
    return myDelegate.isValid();
  }

  @Override
  public boolean isWritable() {
    return getDelegate().isWritable();
  }

  @Override
  public String toString() {
    return "VirtualFileWindow in " + myDelegate.getPresentableUrl();
  }
}