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
package com.intellij.core;

import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.encoding.EncodingRegistry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.Charset;

/**
 * @author yole
 */
public class CoreEncodingRegistry extends EncodingRegistry {
  @Override
  public boolean isNative2Ascii(@NotNull VirtualFile virtualFile) {
    return false;
  }

  @Override
  public Charset getDefaultCharset() {
    return CharsetToolkit.getDefaultSystemCharset();
  }

  @Override
  public Charset getEncoding(@Nullable VirtualFile virtualFile, boolean useParentDefaults) {
    return getDefaultCharset();
  }

  @Override
  public boolean isUseUTFGuessing(VirtualFile virtualFile) {
    return true;
  }

  @Override
  public void setEncoding(@Nullable VirtualFile virtualFileOrDir, @Nullable Charset charset) {
  }
}
