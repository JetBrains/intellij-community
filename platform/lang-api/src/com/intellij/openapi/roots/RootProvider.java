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
package com.intellij.openapi.roots;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.util.EventListener;

/**
 *  Root provider for order entry
 *  @author dsl
 */
public interface RootProvider {
  @NotNull String[] getUrls(@NotNull OrderRootType rootType);
  @NotNull VirtualFile[] getFiles(@NotNull OrderRootType rootType);

  interface RootSetChangedListener extends EventListener {
    void rootSetChanged(RootProvider wrapper);
  }

  void addRootSetChangedListener(@NotNull RootSetChangedListener listener);
  void addRootSetChangedListener(@NotNull RootSetChangedListener listener, @NotNull Disposable parentDisposable);
  void removeRootSetChangedListener(@NotNull RootSetChangedListener listener);
}
