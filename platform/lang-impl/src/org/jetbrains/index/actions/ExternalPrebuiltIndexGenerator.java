// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.index.actions;

import com.intellij.index.PrebuiltFileBasedIndexProvider;
import com.intellij.index.PrebuiltFileBasedIndexProviderGenerator;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.SystemProperties;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.indexing.ID;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Arrays;
import java.util.Objects;
import java.util.Set;

public class ExternalPrebuiltIndexGenerator implements PrebuiltFileBasedIndexProviderGenerator {
  private static final String EXTERNAL_PREBUILT_INDEX_PATH = System.getProperty("idea.prebuilt.indices.path");
  @Nullable
  @Override
  public <K, V> PrebuiltFileBasedIndexProvider<K, V> generateProvider(@NotNull ID<K, V> id) {
    if (EXTERNAL_PREBUILT_INDEX_PATH == null) return null;
    File dir = new File(EXTERNAL_PREBUILT_INDEX_PATH);
    if (!dir.isDirectory()) {
      return null;
    }
    if (Arrays.stream(Objects.requireNonNull(dir.listFiles())).noneMatch(f -> f.getName().equals(id.getName()))) return null;

    return new PrebuiltFileBasedIndexProvider<K, V>(id) {
      @NotNull
      @Override
      public Set<FileType> getFileTypes() {
        return ContainerUtil.set(FileTypeManager.getInstance().getRegisteredFileTypes());
      }

      @NotNull
      @Override
      protected String getDirName() {
        return EXTERNAL_PREBUILT_INDEX_PATH;
      }
    };
  }
}
