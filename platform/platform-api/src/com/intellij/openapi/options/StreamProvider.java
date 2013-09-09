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
package com.intellij.openapi.options;

import com.intellij.openapi.components.RoamingType;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;

public interface StreamProvider {
  StreamProvider[] EMPTY_ARRAY = new StreamProvider[0];

  StreamProvider DEFAULT = new StreamProvider() {
    @Override
    public void saveContent(@NotNull String fileSpec, @NotNull InputStream content, long size, @NotNull RoamingType roamingType, boolean async) throws IOException {

    }

    @Override
    public InputStream loadContent(@NotNull String fileSpec, @NotNull RoamingType roamingType) throws IOException {
      return null;
    }

    @Override
    public String[] listSubFiles(@NotNull String fileSpec, @NotNull RoamingType roamingType) {
      return ArrayUtil.EMPTY_STRING_ARRAY;
    }

    @Override
    public void deleteFile(@NotNull String fileSpec, @NotNull RoamingType roamingType) {
    }

    @Override
    public boolean isEnabled() {
      return false;
    }
  };

  void saveContent(@NotNull String fileSpec, @NotNull InputStream content, long size, @NotNull RoamingType roamingType, boolean async) throws IOException;

  @Nullable
  InputStream loadContent(@NotNull String fileSpec, @NotNull RoamingType roamingType) throws IOException;

  String[] listSubFiles(@NotNull String fileSpec, @NotNull RoamingType roamingType);

  void deleteFile(@NotNull String fileSpec, @NotNull RoamingType roamingType);

  boolean isEnabled();
}