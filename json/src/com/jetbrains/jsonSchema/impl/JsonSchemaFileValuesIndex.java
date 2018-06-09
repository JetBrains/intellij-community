// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.jsonSchema.impl;

import com.intellij.json.JsonFileType;
import com.intellij.json.json5.Json5FileType;
import com.intellij.json.psi.JsonFile;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.indexing.*;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.EnumeratorStringDescriptor;
import com.intellij.util.io.KeyDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class JsonSchemaFileValuesIndex extends FileBasedIndexExtension<String, String> {
  public static final ID<String, String> INDEX_ID = ID.create("json.file.root.values");
  private static final int VERSION = 1;
  public static final String NULL = "$NULL$";

  @NotNull
  @Override
  public ID<String, String> getName() {
    return INDEX_ID;
  }

  private final DataIndexer<String, String, FileContent> myIndexer =
    new DataIndexer<String, String, FileContent>() {
      @Override
      @NotNull
      public Map<String, String> map(@NotNull FileContent inputData) {
        PsiFile file = inputData.getPsiFile();
        if (!(file instanceof JsonFile)) return ContainerUtil.newHashMap();
        HashMap<String, String> map = ContainerUtil.newHashMap();
        String schemaUrl = JsonCachedValues.fetchSchemaUrl(file);
        map.put(JsonCachedValues.URL_CACHE_KEY, schemaUrl == null ? NULL : schemaUrl);
        String schemaId = JsonCachedValues.fetchSchemaId(file);
        map.put(JsonCachedValues.ID_CACHE_KEY, schemaId == null ? NULL : schemaId);
        return map;
      }
    };

  @NotNull
  @Override
  public DataIndexer<String, String, FileContent> getIndexer() {
    return myIndexer;
  }

  @NotNull
  @Override
  public KeyDescriptor<String> getKeyDescriptor() {
    return EnumeratorStringDescriptor.INSTANCE;
  }

  @NotNull
  @Override
  public DataExternalizer<String> getValueExternalizer() {
    return new DataExternalizer<String>() {
      @Override
      public void save(@NotNull DataOutput out, String value) throws IOException {
        out.writeUTF(value);
      }

      @Override
      public String read(@NotNull DataInput in) throws IOException {
        return in.readUTF();
      }
    };
  }

  @Override
  public int getVersion() {
    return VERSION;
  }

  @NotNull
  @Override
  public FileBasedIndex.InputFilter getInputFilter() {
    return new DefaultFileTypeSpecificInputFilter(JsonFileType.INSTANCE, Json5FileType.INSTANCE);
  }

  @Override
  public boolean dependsOnFileContent() {
    return true;
  }

  @Nullable
  public static String getCachedValue(Project project, VirtualFile file, String requestedKey) {
    if (project.isDisposed() || !file.isValid() || DumbService.isDumb(project)) return NULL;
    Collection<String> keys = FileBasedIndex.getInstance().getAllKeys(INDEX_ID, project);
    for (String key: keys) {
      if (requestedKey.equals(key)) {
        List<String> values = FileBasedIndex.getInstance().getValues(INDEX_ID, key, GlobalSearchScope.fileScope(project, file));
        if (values.size() == 1) {
          return values.get(0);
        }
      }
    }

    return null;
  }
}
