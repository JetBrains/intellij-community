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
package com.intellij.openapi.vfs;

import org.jetbrains.annotations.NotNull;

@FunctionalInterface
public interface VirtualFileFilter {
  boolean accept(@NotNull VirtualFile file);

  VirtualFileFilter ALL = new VirtualFileFilter() {
    @Override
    public boolean accept(@NotNull VirtualFile file) {
      return true;
    }

    @Override
    public String toString() {
      return "ALL";
    }
  };

  VirtualFileFilter NONE = new VirtualFileFilter() {
    @Override
    public boolean accept(@NotNull VirtualFile file) {
      return false;
    }

    @Override
    public String toString() {
      return "NONE";
    }
  };

  @NotNull
  default VirtualFileFilter and(@NotNull VirtualFileFilter other) {
    return file -> accept(file) && other.accept(file);
  }
}