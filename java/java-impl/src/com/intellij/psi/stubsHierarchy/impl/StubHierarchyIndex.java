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
package com.intellij.psi.stubsHierarchy.impl;

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.impl.java.stubs.hierarchy.IndexTree;
import com.intellij.psi.impl.java.stubs.index.JavaUnitDescriptor;
import com.intellij.psi.stubsHierarchy.StubHierarchyIndexer;
import com.intellij.util.indexing.*;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.EnumeratorStringDescriptor;
import com.intellij.util.io.KeyDescriptor;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * @author peter
 */
public class StubHierarchyIndex extends FileBasedIndexExtension<String, IndexTree.Unit> implements PsiDependentIndex {
  static final ID<String, IndexTree.Unit> INDEX_ID = ID.create("jvm.hierarchy");
  private static final StubHierarchyIndexer[] ourIndexers = StubHierarchyIndexer.EP_NAME.getExtensions();

  @NotNull
  @Override
  public ID<String, IndexTree.Unit> getName() {
    return INDEX_ID;
  }

  @NotNull
  @Override
  public DataIndexer<String, IndexTree.Unit, FileContent> getIndexer() {
    return inputData -> {
      for (StubHierarchyIndexer indexer : ourIndexers) {
        List<Pair<String, IndexTree.Unit>> pairs = indexer.handlesFile(inputData.getFile()) ? indexer.indexFile(inputData) : null;
        if (pairs != null && !pairs.isEmpty()) {
          Map<String, IndexTree.Unit> answer = new HashMap<>();
          for (Pair<String, IndexTree.Unit> entry : pairs) {
            if (entry.second.myDecls.length > 0) {
              answer.put(StringUtil.notNullize(entry.first), entry.second);
            }
          }
          return answer;
        }
      }
      return Collections.emptyMap();
    };
  }

  @NotNull
  @Override
  public KeyDescriptor<String> getKeyDescriptor() {
    return EnumeratorStringDescriptor.INSTANCE;
  }

  @NotNull
  @Override
  public DataExternalizer<IndexTree.Unit> getValueExternalizer() {
    return JavaUnitDescriptor.INSTANCE;
  }

  @Override
  public int getVersion() {
    return IndexTree.STUB_HIERARCHY_ENABLED ? 1 + Arrays.stream(ourIndexers).mapToInt(StubHierarchyIndexer::getVersion).sum() : 0;
  }

  @NotNull
  @Override
  public FileBasedIndex.InputFilter getInputFilter() {
    return file -> IndexTree.STUB_HIERARCHY_ENABLED && Arrays.stream(ourIndexers).anyMatch(indexer -> indexer.handlesFile(file));
  }

  @Override
  public boolean dependsOnFileContent() {
    return true;
  }

}
