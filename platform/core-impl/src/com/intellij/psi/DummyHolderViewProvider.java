// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi;

import com.intellij.lang.FileASTNode;
import com.intellij.lang.Language;
import com.intellij.openapi.fileTypes.UnknownFileType;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.impl.SharedPsiElementImplUtil;
import com.intellij.psi.impl.source.DummyHolder;
import com.intellij.psi.impl.source.tree.LeafElement;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.util.LocalTimeCounter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Set;

public class DummyHolderViewProvider extends AbstractFileViewProvider {
  private DummyHolder myHolder;
  private final long myModificationStamp;

  public DummyHolderViewProvider(@NotNull PsiManager manager) {
    super(manager, new LightVirtualFile("DummyHolder", UnknownFileType.INSTANCE, ""), false);
    myModificationStamp = LocalTimeCounter.currentTime();
  }

  @Override
  public @NotNull CharSequence getContents() {
    return myHolder != null ? myHolder.getNode().getText() : "";
  }

  @Override
  public @NotNull Language getBaseLanguage() {
    return myHolder.getLanguage();
  }

  @Override
  public @NotNull Set<Language> getLanguages() {
    return Collections.singleton(getBaseLanguage());
  }

  @Override
  protected @Nullable PsiFile getPsiInner(@NotNull Language target) {
    return getCachedPsi(target);
  }

  @Override
  public PsiFile getCachedPsi(@NotNull Language target) {
    getManager().getFileManager().setViewProvider(getVirtualFile(), this);
    return target == getBaseLanguage() ? myHolder : null;
  }

  @Override
  public @NotNull List<PsiFile> getCachedPsiFiles() {
    return Collections.singletonList(myHolder);
  }

  @Override
  public @NotNull List<FileASTNode> getKnownTreeRoots() {
    return Collections.singletonList(myHolder.getTreeElement());
  }

  @Override
  public @NotNull List<PsiFile> getAllFiles() {
    return getCachedPsiFiles();
  }

  @Override
  public long getModificationStamp() {
    return myModificationStamp;
  }

  public void setDummyHolder(@NotNull DummyHolder dummyHolder) {
    myHolder = dummyHolder;
    ((LightVirtualFile)getVirtualFile()).setFileType(dummyHolder.getFileType());
  }

  @Override
  public PsiReference findReferenceAt(int offset) {
    return SharedPsiElementImplUtil.findReferenceAt(getPsi(getBaseLanguage()), offset);
  }

  @Override
  public PsiElement findElementAt(int offset, @NotNull Class<? extends Language> lang) {
    if (!lang.isAssignableFrom(getBaseLanguage().getClass())) return null;
    return findElementAt(offset);
  }

  @Override
  public @NotNull FileViewProvider createCopy(@NotNull VirtualFile copy) {
    throw new RuntimeException("Clone is not supported for DummyHolderProviders. Use DummyHolder clone directly.");
  }

  @Override
  public PsiElement findElementAt(int offset) {
    LeafElement element = myHolder.calcTreeElement().findLeafElementAt(offset);
    return element != null ? element.getPsi() : null;
  }
}
