// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.psi.impl.source.tree.injected;

import com.intellij.injected.editor.VirtualFileWindow;
import com.intellij.lang.Language;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.FreeThreadedFileViewProvider;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.psi.templateLanguages.TemplateLanguageFileViewProvider;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

class MultipleRootsInjectedFileViewProvider extends MultiplePsiFilesPerDocumentFileViewProvider implements FreeThreadedFileViewProvider, InjectedFileViewProvider {
  private final Object myLock = new Object();
  private final DocumentWindowImpl myDocumentWindow;
  private final Language myLanguage;
  private boolean myPatchingLeaves;
  protected final AbstractFileViewProvider myOriginalProvider;

  MultipleRootsInjectedFileViewProvider(@NotNull PsiManager psiManager,
                                        @NotNull VirtualFileWindow virtualFile,
                                        @NotNull DocumentWindowImpl documentWindow,
                                        @NotNull Language language,
                                        @NotNull AbstractFileViewProvider original) {
    super(psiManager, (VirtualFile)virtualFile, true);
    myDocumentWindow = documentWindow;
    myLanguage = language;
    myOriginalProvider = original;
  }

  @Override
  public Object getLock() {
    return myLock;
  }

  @Override
  public boolean getPatchingLeaves() {
    return myPatchingLeaves;
  }

  @Override
  public FileViewProvider clone() {
    return cloneImpl();
  }

  @Override
  public void rootChanged(@NotNull PsiFile psiFile) {
    super.rootChanged(psiFile);
    rootChangedImpl(psiFile);
  }

  @Override
  public boolean isEventSystemEnabled() {
    return isEventSystemEnabledImpl();
  }

  @Override
  public boolean isPhysical() {
    return isPhysicalImpl();
  }

  @NotNull
  @Override
  public Language getBaseLanguage() {
    return myLanguage;
  }

  @NotNull
  @Override
  public Set<Language> getLanguages() {
    FileViewProvider original = myOriginalProvider;
    Set<Language> languages = original.getLanguages();
    Language base = original.getBaseLanguage();
    return ContainerUtil.map2Set(languages, (language) -> language == base ? myLanguage : language);
  }

  @NotNull
  @Override
  protected MultiplePsiFilesPerDocumentFileViewProvider cloneInner(@NotNull VirtualFile fileCopy) {
    FileViewProvider originalProvider = getManager().getFileManager().createFileViewProvider(fileCopy, false);
    assert originalProvider instanceof MultiplePsiFilesPerDocumentFileViewProvider :
      "Original provider " + originalProvider + " is not multi-root for " + fileCopy + ", cached provider: " + myOriginalProvider;
    return (MultiplePsiFilesPerDocumentFileViewProvider)originalProvider;
  }

  @Override
  @NotNull
  public DocumentWindowImpl getDocument() {
    return myDocumentWindow;
  }

  @NonNls
  @Override
  public String toString() {
    return "Multi root injected file '"+getVirtualFile().getName()+"' " + (isValid() ? "" : " invalid") + (isPhysical() ? "" : " nonphysical");
  }

  @Override
  public final void forceCachedPsi(@NotNull PsiFile psiFile) {
    myRoots.put(psiFile.getLanguage(), (PsiFileImpl)psiFile);
    getManager().getFileManager().setViewProvider(getVirtualFile(), this);
  }

  public void doNotInterruptMeWhileImPatchingLeaves(@NotNull Runnable runnable) {
    myPatchingLeaves = true;
    try {
      runnable.run();
    }
    finally {
      myPatchingLeaves = false;
    }
  }

  static final class Template extends MultipleRootsInjectedFileViewProvider implements TemplateLanguageFileViewProvider {
    Template(@NotNull PsiManagerEx psiManager,
             @NotNull VirtualFileWindow virtualFile,
             @NotNull DocumentWindowImpl documentWindow,
             @NotNull Language language,
             AbstractFileViewProvider original) {
      super(psiManager, virtualFile, documentWindow, language, original);
      assert myOriginalProvider instanceof TemplateLanguageFileViewProvider;
    }

    @NotNull
    @Override
    public Language getTemplateDataLanguage() {
      return ((TemplateLanguageFileViewProvider)myOriginalProvider).getTemplateDataLanguage();
    }

    @Override
    public @Nullable IElementType getContentElementType(@NotNull Language language) {
      return ((TemplateLanguageFileViewProvider)myOriginalProvider).getContentElementType(language);
    }
  }
}
