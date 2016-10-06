/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.jetbrains.jsonSchema.extension.schema;

import com.intellij.json.psi.JsonFile;
import com.intellij.json.psi.JsonProperty;
import com.intellij.json.psi.impl.JsonRecursiveElementVisitor;
import com.intellij.psi.PsiFile;
import com.intellij.util.indexing.*;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.EnumeratorStringDescriptor;
import com.intellij.util.io.IntInlineKeyDescriptor;
import com.intellij.util.io.KeyDescriptor;
import com.jetbrains.jsonSchema.JsonSchemaFileType;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Irina.Chernushina on 4/1/2016.
 */
public class JsonSchemaFileIndex extends FileBasedIndexExtension<String, Integer> {
  public static final ID<String, Integer> PROPERTIES_INDEX = ID.create("json.schema.properties.index");
  public static final int VERSION = 9;
  private IntInlineKeyDescriptor myKeyDescriptor = new IntInlineKeyDescriptor();

  @NotNull
  @Override
  public ID<String, Integer> getName() {
    return PROPERTIES_INDEX;
  }

  @NotNull
  @Override
  public DataIndexer<String, Integer, FileContent> getIndexer() {
    return new DataIndexer<String, Integer, FileContent>() {
      @NotNull
      @Override
      public Map<String, Integer> map(@NotNull FileContent inputData) {
        final Map<String, Integer> map = new HashMap<>();
        final PsiFile file = inputData.getPsiFile();
        if (file instanceof JsonFile) {
          file.accept(new JsonRecursiveElementVisitor() {
            private String myPrefix = "";
            @Override
            public void visitProperty(@NotNull JsonProperty property) {
              String wasPrefix = myPrefix;
              myPrefix = myPrefix + "/" + property.getName();
              map.put(myPrefix, property.getTextRange().getStartOffset());
              super.visitProperty(property);
              myPrefix = wasPrefix;
            }
          });
        }
        return map;
      }
    };
  }

  @NotNull
  @Override
  public KeyDescriptor<String> getKeyDescriptor() {
    return EnumeratorStringDescriptor.INSTANCE;
  }

  @NotNull
  @Override
  public DataExternalizer<Integer> getValueExternalizer() {
    return myKeyDescriptor;
  }

  @Override
  public int getVersion() {
    return VERSION;
  }

  @NotNull
  @Override
  public FileBasedIndex.InputFilter getInputFilter() {
    return new DefaultFileTypeSpecificInputFilter(JsonSchemaFileType.INSTANCE);
  }

  @Override
  public boolean dependsOnFileContent() {
    return true;
  }
}
