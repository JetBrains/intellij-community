package com.intellij.psi;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.lang.Language;

/**
 * @author yole
 */
public interface FileViewProviderFactory {
  FileViewProvider createFileViewProvider(final VirtualFile file, final Language language, final PsiManager manager, final boolean physical);
}