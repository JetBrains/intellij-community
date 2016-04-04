/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

package com.intellij.psi.impl.source;

import com.intellij.lang.Language;
import com.intellij.openapi.fileTypes.*;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.FileElement;
import com.intellij.psi.impl.source.tree.SharedImplUtil;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.util.CharTable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class DummyHolder extends PsiFileImpl {
  protected final PsiElement myContext;
  private final CharTable myTable;
  private final Boolean myExplicitlyValid;
  private final Language myLanguage;
  private volatile FileElement myFileElement = null;
  @SuppressWarnings("EmptyClass") private static class DummyHolderTreeLock {}
  private final DummyHolderTreeLock myTreeElementLock = new DummyHolderTreeLock();

  public DummyHolder(@NotNull PsiManager manager, TreeElement contentElement, PsiElement context) {
    this(manager, contentElement, context, SharedImplUtil.findCharTableByTree(contentElement));
  }

  public DummyHolder(@NotNull PsiManager manager, CharTable table, boolean validity) {
    this(manager, null, null, table, Boolean.valueOf(validity), PlainTextLanguage.INSTANCE);
  }

  public DummyHolder(@NotNull PsiManager manager, PsiElement context) {
    this(manager, null, context, null);
  }

  public DummyHolder(@NotNull PsiManager manager, @Nullable TreeElement contentElement, PsiElement context, @Nullable CharTable table) {
    this(manager, contentElement, context, table, null, language(context, PlainTextLanguage.INSTANCE));
  }

  protected static Language language(PsiElement context, Language defaultLanguage) {
    if (context == null) return defaultLanguage;
    PsiFile file = context.getContainingFile();
    if (file == null) return defaultLanguage;
    Language contextLanguage = context.getLanguage();
    Language language = file.getLanguage();
    if (language.isKindOf(contextLanguage)) return language;
    return contextLanguage;
  }

  public DummyHolder(@NotNull PsiManager manager, @Nullable TreeElement contentElement, @Nullable PsiElement context, @Nullable CharTable table, @Nullable Boolean validity, Language language) {
    super(TokenType.DUMMY_HOLDER, TokenType.DUMMY_HOLDER, new DummyHolderViewProvider(manager));
    myLanguage = language;
    ((DummyHolderViewProvider)getViewProvider()).setDummyHolder(this);
    myContext = context;
    myTable = table != null ? table : IdentityCharTable.INSTANCE;
    if (contentElement instanceof FileElement) {
      myFileElement = (FileElement)contentElement;
      myFileElement.setPsi(this);
      myFileElement.setCharTable(myTable);
    }
    else if (contentElement != null) {
      getTreeElement().rawAddChildren(contentElement);
      clearCaches();
    }
    myExplicitlyValid = validity;
  }

  public DummyHolder(@NotNull PsiManager manager, PsiElement context, CharTable table) {
    this(manager, null, context, table);
  }

  public DummyHolder(@NotNull PsiManager manager, final CharTable table, final Language language) {
    this(manager, null, null, table, null, language);
  }

  public DummyHolder(@NotNull PsiManager manager, final Language language, final PsiElement context) {
    this(manager, null, context, null, null, language);
  }

  @Override
  public PsiElement getContext() {
    return myContext != null && myContext.isValid() ? myContext : super.getContext();
  }

  @Override
  public boolean isValid() {
    if (myExplicitlyValid != null) return myExplicitlyValid.booleanValue();
    return super.isValid();
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    visitor.visitFile(this);
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public String toString() {
    return "DummyHolder";
  }

  @Override
  @NotNull
  public FileType getFileType() {
    PsiElement context = getContext();
    if (context != null) {
      PsiFile containingFile = context.getContainingFile();
      if (containingFile != null) return containingFile.getFileType();
    }
    final LanguageFileType fileType = myLanguage.getAssociatedFileType();
    return fileType != null ? fileType : PlainTextFileType.INSTANCE;
  }

  @Override
  public FileElement getTreeElement() {
    FileElement fileElement = myFileElement;
    if (fileElement != null) return fileElement;

    synchronized (myTreeElementLock) {
      fileElement = myFileElement;
      if (fileElement == null) {
        fileElement = new FileElement(TokenType.DUMMY_HOLDER, null);
        fileElement.setPsi(this);
        if (myTable != null) fileElement.setCharTable(myTable);
        myFileElement = fileElement;
        clearCaches();
      }
      return fileElement;
    }
  }

  @Override
  @NotNull
  public Language getLanguage() {
    return myLanguage;
  }

  @Override
  @SuppressWarnings({"CloneDoesntDeclareCloneNotSupportedException"})
  protected PsiFileImpl clone() {
    final PsiFileImpl psiClone = cloneImpl(myFileElement);
    final DummyHolderViewProvider dummyHolderViewProvider = new DummyHolderViewProvider(getManager());
    myViewProvider = dummyHolderViewProvider;
    dummyHolderViewProvider.setDummyHolder((DummyHolder)psiClone);
    final FileElement treeClone = (FileElement)calcTreeElement().clone();
    psiClone.setTreeElementPointer(treeClone); // should not use setTreeElement here because cloned file still have VirtualFile (SCR17963)
    psiClone.myOriginalFile = isPhysical() ? this : myOriginalFile;
    treeClone.setPsi(psiClone);
    return psiClone;
  }

  private FileViewProvider myViewProvider = null;

  @Override
  @NotNull
  public FileViewProvider getViewProvider() {
    if(myViewProvider != null) return myViewProvider;
    return super.getViewProvider();
  }
}
