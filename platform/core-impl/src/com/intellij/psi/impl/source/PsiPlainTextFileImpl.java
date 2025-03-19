// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.psi.impl.source;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.openapi.fileTypes.PlainTextLanguage;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.reference.ReferenceProvidersRegistry;
import org.jetbrains.annotations.NotNull;

public class PsiPlainTextFileImpl extends PsiFileImpl implements PsiPlainTextFile, HintedReferenceHost {
  private final FileType myFileType;

  public PsiPlainTextFileImpl(FileViewProvider viewProvider) {
    super(PlainTextTokenTypes.PLAIN_TEXT_FILE, PlainTextTokenTypes.PLAIN_TEXT_FILE, viewProvider);
    myFileType = viewProvider.getBaseLanguage() != PlainTextLanguage.INSTANCE ? PlainTextFileType.INSTANCE : viewProvider.getFileType();
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor){
    visitor.visitPlainTextFile(this);
  }

  @Override
  public String toString(){
    return "PsiFile(plain text):" + getName();
  }

  @Override
  public @NotNull FileType getFileType() {
    return myFileType;
  }

  @Override
  public PsiReference @NotNull [] getReferences() {
    return ReferenceProvidersRegistry.getReferencesFromProviders(this);
  }

  @Override
  public PsiReference @NotNull [] getReferences(PsiReferenceService.@NotNull Hints hints) {
    return ReferenceProvidersRegistry.getReferencesFromProviders(this, hints);
  }

  @Override
  public boolean shouldAskParentForReferences(PsiReferenceService.@NotNull Hints hints) {
    return false;
  }
}
