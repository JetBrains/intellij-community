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
import com.intellij.psi.impl.java.stubs.hierarchy.IndexTree;
import com.intellij.psi.impl.java.stubs.index.JavaUnitDescriptor;
import com.intellij.psi.stubsHierarchy.StubHierarchyIndexer;
import com.intellij.util.indexing.*;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.EnumeratorIntegerDescriptor;
import com.intellij.util.io.KeyDescriptor;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collections;

/**
 * @author peter
 */
public class StubHierarchyIndex extends FileBasedIndexExtension<Integer, IndexTree.Unit> implements PsiDependentIndex {
  public static final int BINARY_FILES = 0;
  public static final int SOURCE_FILES = 1;
  static final ID<Integer, IndexTree.Unit> INDEX_ID = ID.create("jvm.hierarchy");
  private static final StubHierarchyIndexer[] ourIndexers = StubHierarchyIndexer.EP_NAME.getExtensions();

  @NotNull
  @Override
  public ID<Integer, IndexTree.Unit> getName() {
    return INDEX_ID;
  }

  @NotNull
  @Override
  public DataIndexer<Integer, IndexTree.Unit, FileContent> getIndexer() {
    return inputData -> {
      for (StubHierarchyIndexer indexer : ourIndexers) {
        IndexTree.Unit unit = indexer.handlesFile(inputData.getFile()) ? indexer.indexFile(inputData) : null;
        if (unit != null && unit.myDecls.length > 0) {
          return Collections.singletonMap(inputData.getFile().getFileType().isBinary() ? BINARY_FILES : SOURCE_FILES, unit);
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
  public DataExternalizer<IndexTree.Unit> getValueExternalizer() {
    return JavaUnitDescriptor.INSTANCE;
  }

  @Override
  public int getVersion() {
    return IndexTree.STUB_HIERARCHY_ENABLED ? 3 + Arrays.stream(ourIndexers).mapToInt(StubHierarchyIndexer::getVersion).sum() : 0;
  }

  @NotNull
  @Override
  public FileBasedIndex.InputFilter getInputFilter() {
    return file -> IndexTree.STUB_HIERARCHY_ENABLED &&
                   Arrays.stream(ourIndexers).anyMatch(indexer -> indexer.handlesFile(file)) &&
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
