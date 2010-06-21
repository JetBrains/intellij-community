/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.psi.impl.search;

import com.intellij.openapi.application.QueryExecutorBase;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.meta.PsiMetaData;
import com.intellij.psi.meta.PsiMetaOwner;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.util.Processor;

/**
 * @author max
 */
public class CachesBasedRefSearcher extends QueryExecutorBase<PsiReference, ReferencesSearch.SearchParameters> {
  public CachesBasedRefSearcher() {
    super(true);
  }

  @Override
  public void processQuery(ReferencesSearch.SearchParameters p, Processor<PsiReference> consumer) {
    final PsiElement refElement = p.getElementToSearch();

    String text = null;
    if (refElement instanceof PsiFile) {
      final VirtualFile vFile = ((PsiFile)refElement).getVirtualFile();
      if (vFile != null) {
        text = vFile.getNameWithoutExtension();
      }
    }
    else if (refElement instanceof PsiNamedElement) {
      text = ((PsiNamedElement)refElement).getName();
      if (refElement instanceof PsiMetaOwner) {
        final PsiMetaData metaData = ((PsiMetaOwner)refElement).getMetaData();
        if (metaData != null) text = metaData.getName();
      }
    }

    if (text == null && refElement instanceof PsiMetaOwner) {
      final PsiMetaData metaData = ((PsiMetaOwner)refElement).getMetaData();
      if (metaData != null) text = metaData.getName();
    }
    if (StringUtil.isNotEmpty(text)) {
      final SearchScope searchScope = p.getEffectiveSearchScope();
      assert text != null;
      p.getOptimizer().searchWord(text, searchScope, refElement.getLanguage().isCaseSensitive(), refElement);
    }
  }

}
