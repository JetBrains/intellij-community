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

/*
 * @author max
 */
package com.intellij.injected.editor;

import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

/**
 * An interface implemented by a special {@link VirtualFile} used for injected PSI.
 * @see com.intellij.lang.injection.InjectedLanguageManager
 */
public interface VirtualFileWindow {
  /**
   * @return the "real" virtual file hosting the injected PSI that this "window" corresponds to.
   */
  @NotNull
  VirtualFile getDelegate();

  /**
   * @return the document corresponding to this virtual file, which is a document "window" based on text ranges inside host document.
   */
  @NotNull
  DocumentWindow getDocumentWindow();

  /**
   * @return whether this injected file hasn't been invalidated (which could happen
   * e.g. if its document window ranges were removed or injected PSI was invalidated).
   */
  boolean isValid();
}
