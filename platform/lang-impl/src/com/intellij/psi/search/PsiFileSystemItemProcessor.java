package com.intellij.psi.search;

import com.intellij.psi.PsiFileSystemItem;

/**
 * @author Dmitry Avdeev
 */
public interface PsiFileSystemItemProcessor extends PsiElementProcessor<PsiFileSystemItem> {

  boolean acceptItem(String name, boolean isDirectory);
}
