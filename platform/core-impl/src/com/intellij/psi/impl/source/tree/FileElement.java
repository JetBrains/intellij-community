/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

package com.intellij.psi.impl.source.tree;

import com.intellij.lang.*;
import com.intellij.openapi.util.Getter;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.psi.impl.source.CharTableImpl;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.IFileElementType;
import com.intellij.psi.tree.ILightStubFileElementType;
import com.intellij.util.CharTable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class FileElement extends LazyParseableElement implements FileASTNode, Getter<FileElement> {
  public static final FileElement[] EMPTY_ARRAY = new FileElement[0];
  private volatile CharTable myCharTable = new CharTableImpl();
  private volatile boolean myDetached;

  @Override
  protected PsiElement createPsiNoLock() {
    return myDetached ? null : super.createPsiNoLock();
  }

  public void detachFromFile() {
    myDetached = true;
    clearPsi();
  }

  @Override
  @NotNull
  public CharTable getCharTable() {
    return myCharTable;
  }

  @Nullable
  @Override
  public LighterAST getLighterAST() {
    final IFileElementType contentType = (IFileElementType)getElementType();
    assert contentType instanceof ILightStubFileElementType:contentType; // method should not be called for such element types

    if (!isParsed()) {
      return new FCTSBackedLighterAST(getCharTable(), ((ILightStubFileElementType<?>)contentType).parseContentsLight(this));
    }
    return new TreeBackedLighterAST(this);
  }

  public FileElement(@NotNull IElementType type, CharSequence text) {
    super(type, text);
  }

  @Deprecated  // for 8.1 API compatibility
  public FileElement(IElementType type) {
    super(type, null);
  }

  @Override
  public PsiManagerEx getManager() {
    CompositeElement treeParent = getTreeParent();
    if (treeParent != null) return treeParent.getManager();
    return (PsiManagerEx)getPsi().getManager(); //TODO: cache?
  }

  @Override
  public ASTNode copyElement() {
    PsiFileImpl psiElement = (PsiFileImpl)getPsi();
    PsiFileImpl psiElementCopy = (PsiFileImpl)psiElement.copy();
    return psiElementCopy.getTreeElement();
  }

  public void setCharTable(@NotNull CharTable table) {
    myCharTable = table;
  }

  @Override
  public FileElement get() {
    return this;
  }
}
