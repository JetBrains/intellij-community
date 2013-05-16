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
package com.intellij.openapi.vfs.encoding;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Getter;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.Charset;

/**
 * @author yole
 */
public abstract class EncodingRegistry {
  public static Getter<EncodingRegistry> ourInstanceGetter;

  public abstract boolean isNative2Ascii(@NotNull VirtualFile virtualFile);

  /**
   * @return charset configured in Settings|File Encodings|IDE encoding
   */
  @Nullable
  public abstract Charset getDefaultCharset();

  /**
   * @param virtualFile  file to get encoding for
   * @param useParentDefaults true to determine encoding from the parent
   * @return encoding configured for this file in Settings|File Encodings or,
   *         if useParentDefaults is true, encoding configured for nearest parent of virtualFile or,
   *         null if there is no configured encoding found.
   */
  @Nullable
  public abstract Charset getEncoding(@Nullable VirtualFile virtualFile, boolean useParentDefaults);

  public abstract boolean isUseUTFGuessing(VirtualFile virtualFile);

  public abstract void setEncoding(@Nullable("null means project") VirtualFile virtualFileOrDir, @Nullable("null means remove mapping") Charset charset);

  public static EncodingRegistry getInstance() {
    if (ourInstanceGetter == null) {
      return (EncodingRegistry)ApplicationManager.getApplication().getPicoContainer().getComponentInstance("com.intellij.openapi.vfs.encoding.EncodingManager");
    }
    return ourInstanceGetter.get();
  }


  public static <E extends Throwable> VirtualFile doActionAndRestoreEncoding(@NotNull VirtualFile fileBefore,
                                                                             @NotNull ThrowableComputable<VirtualFile, E> action) throws E {
    EncodingRegistry registry = getInstance();
    Charset charsetBefore = registry.getEncoding(fileBefore, true);
    VirtualFile fileAfter = null;
    try {
      fileAfter = action.compute();
      return fileAfter;
    }
    finally {
      if (fileAfter != null) {
        Charset actual = registry.getEncoding(fileAfter, true);
        if (!Comparing.equal(actual, charsetBefore)) {
          registry.setEncoding(fileAfter, charsetBefore);
        }
      }
    }
  }
}
