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
import com.intellij.psi.stubsHierarchy.SmartClassAnchor;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class StubClassAnchor extends SmartClassAnchor {
  public static final StubClassAnchor[] EMPTY_ARRAY = new StubClassAnchor[0];

  public final int myId;
  public final int myFileId;
  final int myStubId;

  StubClassAnchor(int symbolId, ClassAnchor classAnchor) {
    myId = symbolId;
    myFileId = classAnchor.myFileId;
    myStubId = classAnchor.myStubId;
  }

  @Override
  @NotNull
  public VirtualFile retrieveFile() {
    VirtualFile file = PersistentFS.getInstance().findFileById(myFileId);
    assert file != null : this;
    return file;
  }

  @Override
  @NotNull
  public PsiClass retrieveClass(@NotNull Project project) {
    PsiFile psiFile = PsiManager.getInstance(project).findFile(retrieveFile());
    assert psiFile != null : this;
    PsiElement element = restoreFromStubIndex((PsiFileWithStubSupport)psiFile, myStubId);
    if (!(element instanceof PsiClass)) {
      throw new AssertionError(this + "; " + psiFile);
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

  @Override
  public int hashCode() {
    return myId;
  }

  @Override
  public String toString() {
    return myStubId + " in " + retrieveFile().getPath();
  }
}
