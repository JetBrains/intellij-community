package com.intellij.psi.impl.source;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.reference.ReferenceProvidersRegistry;
import org.jetbrains.annotations.NotNull;

public class PsiPlainTextFileImpl extends PsiFileImpl implements PsiPlainTextFile{
  private final FileType myFileType;

  public PsiPlainTextFileImpl(FileViewProvider viewProvider) {
    super(TokenType.PLAIN_TEXT_FILE, TokenType.PLAIN_TEXT, viewProvider);
    myFileType = viewProvider.getVirtualFile().getFileType();
  }

  public void accept(@NotNull PsiElementVisitor visitor){
    visitor.visitPlainTextFile(this);
  }

  public String toString(){
    return "PsiFile(plain text):" + getName();
  }

  @NotNull
  public FileType getFileType() {
    return myFileType;
  }

  @NotNull
  public PsiReference[] getReferences() {
    return ReferenceProvidersRegistry.getReferencesFromProviders(this,PsiPlainTextFile.class);
  }
}
