/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.psi;

import com.intellij.lang.Language;
import com.intellij.openapi.fileTypes.UnknownFileType;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.impl.SharedPsiElementImplUtil;
import com.intellij.psi.impl.source.DummyHolder;
import com.intellij.psi.impl.source.tree.FileElement;
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
    super(manager, new LightVirtualFile("DummyHolder"), false, UnknownFileType.INSTANCE);
    myModificationStamp = LocalTimeCounter.currentTime();
  }

  @Override
  @NotNull
  public CharSequence getContents() {
    return myHolder != null ? myHolder.getNode().getText() : "";
  }

  @Override
  @NotNull
  public Language getBaseLanguage() {
    return myHolder.getLanguage();
  }

  @Override
  @NotNull
  public Set<Language> getLanguages() {
    return Collections.singleton(getBaseLanguage());
  }

  @Nullable
  @Override
  protected PsiFile getPsiInner(Language target) {
    return getCachedPsi(target);
  }

  @Override
  public PsiFile getCachedPsi(@NotNull Language target) {
    getManager().getFileManager().setViewProvider(getVirtualFile(), this);
    return target == getBaseLanguage() ? myHolder : null;
  }

  @Override
  public List<PsiFile> getCachedPsiFiles() {
    return Collections.singletonList(myHolder);
  }

  @NotNull
  @Override
  public List<FileElement> getKnownTreeRoots() {
    return Collections.singletonList(myHolder.getTreeElement());
  }

  @Override
  @NotNull
  public List<PsiFile> getAllFiles() {
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
  public PsiReference findReferenceAt(final int offset) {
    return SharedPsiElementImplUtil.findReferenceAt(getPsi(getBaseLanguage()), offset);
  }

  @Override
  public PsiElement findElementAt(int offset, @NotNull Class<? extends Language> lang) {
    if (!lang.isAssignableFrom(getBaseLanguage().getClass())) return null;
    return findElementAt(offset);
  }

  @NotNull
  @Override
  public FileViewProvider createCopy(@NotNull final VirtualFile copy) {
    throw new RuntimeException("Clone is not supported for DummyHolderProviders. Use DummyHolder clone directly.");
  }

  @Override
  public PsiElement findElementAt(final int offset) {
    final LeafElement element = myHolder.calcTreeElement().findLeafElementAt(offset);
    return element != null ? element.getPsi() : null;
  }
}
