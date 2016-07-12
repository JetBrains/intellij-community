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

import com.intellij.lang.ASTNode;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFS;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.psi.impl.source.PsiFileWithStubSupport;
import com.intellij.psi.stubs.StubBase;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.stubs.StubTree;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.IntIntHashMap;
import gnu.trove.TByteArrayList;
import gnu.trove.TIntArrayList;
import gnu.trove.TIntIntHashMap;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author peter
 */
class AnchorRepository {
  private static final int MAX_BYTE_VALUE = 255;
  private final TIntArrayList myFileIds = new TIntArrayList(80000);
  private final TByteArrayList myShortStubIds = new TByteArrayList(80000);
  private final TIntIntHashMap myLongStubIds = new IntIntHashMap();

  int registerClass(int fileId, int stubId) {
    int anchorId = myFileIds.size();
    myFileIds.add(fileId);
    if (stubId < MAX_BYTE_VALUE) {
      myShortStubIds.add((byte)stubId);
    } else {
      myShortStubIds.add((byte)MAX_BYTE_VALUE);
      myLongStubIds.put(anchorId, stubId);
    }
    return anchorId;
  }

  StubClassAnchor getAnchor(int anchorId) {
    return new StubClassAnchor(anchorId, getFileId(anchorId), getStubId(anchorId));
  }

  int getFileId(int anchorId) {
    return myFileIds.get(anchorId);
  }

  int getStubId(int anchorId) {
    int stubId = Byte.toUnsignedInt(myShortStubIds.get(anchorId));
    return stubId < MAX_BYTE_VALUE ? stubId : myLongStubIds.get(anchorId);
  }

  int size() {
    return myFileIds.size();
  }

  void trimToSize() {
    myFileIds.trimToSize();
    myShortStubIds.trimToSize();
    myLongStubIds.trimToSize();
  }

  @NotNull
  static VirtualFile retrieveFile(int fileId) {
    return ObjectUtils.assertNotNull(PersistentFS.getInstance().findFileById(fileId));
  }

  static String anchorToString(int stubId, int fileId) {
    return stubId + " in " + retrieveFile(fileId).getPath();
  }

  @NotNull
  PsiClass retrieveClass(@NotNull Project project, int anchorId) {
    return retrieveClass(project, getFileId(anchorId), getStubId(anchorId));
  }

  @NotNull
  static PsiClass retrieveClass(@NotNull Project project, int fileId, int stubId) {
    PsiFile psiFile = PsiManager.getInstance(project).findFile(retrieveFile(fileId));
    assert psiFile != null : anchorToString(stubId, fileId);
    PsiElement element = restoreFromStubIndex((PsiFileWithStubSupport)psiFile, stubId);
    if (!(element instanceof PsiClass)) {
      throw new AssertionError(anchorToString(stubId, fileId) + "; " + psiFile);
    }
    return (PsiClass)element;
  }

  private static PsiElement restoreFromStubIndex(PsiFileWithStubSupport fileImpl, int index) {
    StubTree tree = fileImpl.getStubTree();

    boolean foreign = tree == null;
    if (foreign) {
      if (fileImpl instanceof PsiFileImpl) {
        tree = ((PsiFileImpl)fileImpl).calcStubTree();
      }
      else {
        return null;
      }
    }

    List<StubElement<?>> list = tree.getPlainList();
    if (index >= list.size()) {
      return null;
    }
    StubElement stub = list.get(index);

    if (foreign) {
      final PsiElement cachedPsi = ((StubBase)stub).getCachedPsi();
      if (cachedPsi != null) return cachedPsi;

      final ASTNode ast = fileImpl.findTreeForStub(tree, stub);
      return ast != null ? ast.getPsi() : null;
    }
    return stub.getPsi();
  }

}
