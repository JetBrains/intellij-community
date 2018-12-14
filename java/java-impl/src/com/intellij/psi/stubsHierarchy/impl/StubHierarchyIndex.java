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

import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileWithId;
import com.intellij.psi.impl.java.stubs.hierarchy.IndexTree;
import com.intellij.psi.stubsHierarchy.StubHierarchyIndexer;
import com.intellij.util.indexing.*;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.DataInputOutputUtil;
import com.intellij.util.io.EnumeratorIntegerDescriptor;
import com.intellij.util.io.KeyDescriptor;
import org.jetbrains.annotations.NotNull;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Collections;

/**
 * @author peter
 */
public class StubHierarchyIndex extends FileBasedIndexExtension<Integer, SerializedUnit> implements PsiDependentIndex {
  static final ID<Integer, SerializedUnit> INDEX_ID = ID.create("jvm.hierarchy");

  @NotNull
  @Override
  public ID<Integer, SerializedUnit> getName() {
    return INDEX_ID;
  }

  @NotNull
  @Override
  public DataIndexer<Integer, SerializedUnit, FileContent> getIndexer() {
    return inputData -> {
      for (StubHierarchyIndexer indexer : StubHierarchyIndexer.EP_NAME.getExtensionList()) {
        VirtualFile file = inputData.getFile();
        IndexTree.Unit unit = indexer.handlesFile(file) ? indexer.indexFile(inputData) : null;
        if (unit != null && unit.myDecls.length > 0) {
          int[] keys = file.getFileType().isBinary() ? HierarchyServiceImpl.BINARY_KEYS : HierarchyServiceImpl.SOURCE_KEYS;
          return Collections.singletonMap(keys[((VirtualFileWithId) file).getId() % keys.length], new SerializedUnit(unit));
        }
      }
      return Collections.emptyMap();
    };
  }

  @NotNull
  @Override
  public KeyDescriptor<Integer> getKeyDescriptor() {
    return EnumeratorIntegerDescriptor.INSTANCE;
  }

  @NotNull
  @Override
  public DataExternalizer<SerializedUnit> getValueExternalizer() {
    return new DataExternalizer<SerializedUnit>() {
      @Override
      public void save(@NotNull DataOutput out, SerializedUnit value) throws IOException {
        byte[] bytes = value.getSerializedBytes();
        DataInputOutputUtil.writeINT(out, bytes.length);
        out.write(bytes);
      }

      @Override
      public SerializedUnit read(@NotNull DataInput in) throws IOException {
        byte[] bytes = new byte[DataInputOutputUtil.readINT(in)];
        in.readFully(bytes);
        return new SerializedUnit(bytes);
      }
    };
  }

  @Override
  public int getVersion() {
    return IndexTree.STUB_HIERARCHY_ENABLED ? 8 + StubHierarchyIndexer.EP_NAME.extensions().mapToInt(StubHierarchyIndexer::getVersion).sum()
                                            : 0;
  }

  @NotNull
  @Override
  public FileBasedIndex.InputFilter getInputFilter() {
    return file -> IndexTree.STUB_HIERARCHY_ENABLED &&
                   StubHierarchyIndexer.EP_NAME.extensions().anyMatch(indexer -> indexer.handlesFile(file)) &&
                   isSourceOrLibrary(file);
  }

  private static boolean isSourceOrLibrary(VirtualFile file) {
    for (Project project : ProjectManager.getInstance().getOpenProjects()) {
      ProjectFileIndex index = ProjectFileIndex.SERVICE.getInstance(project);
      if (index.isInLibraryClasses(file) || index.isInSourceContent(file)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public boolean dependsOnFileContent() {
    return true;
  }

}
