package com.intellij.psi.impl.compiled;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.stubs.PsiFileStub;

/**
 * @author ilyas
 */
public interface ClsStubBuilderFactory<T extends PsiFile> {

  ExtensionPointName<ClsStubBuilderFactory> EP_NAME = ExtensionPointName.create("com.intellij.clsStubBuilderFactory");

  PsiFileStub<T> buildFileStub(final VirtualFile file, byte[]  bytes);

  boolean canBeProcessed(final VirtualFile file, byte[] bytes); 
}
