package com.intellij.psi.impl.search;

import com.intellij.openapi.application.QueryExecutorBase;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.SyntheticFileSystemItem;
import com.intellij.psi.meta.PsiMetaData;
import com.intellij.psi.meta.PsiMetaOwner;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;

/**
 * @author max
 */
public class CachesBasedRefSearcher extends QueryExecutorBase<PsiReference, ReferencesSearch.SearchParameters> {
  public CachesBasedRefSearcher() {
    super(true);
  }

  @Override
  public void processQuery(@NotNull ReferencesSearch.SearchParameters p, @NotNull Processor<PsiReference> consumer) {
    final PsiElement refElement = p.getElementToSearch();
    boolean caseSensitive = refElement.getLanguage().isCaseSensitive();

    String text = null;
    if (refElement instanceof PsiFileSystemItem && !(refElement instanceof SyntheticFileSystemItem)) {
      final VirtualFile vFile = ((PsiFileSystemItem)refElement).getVirtualFile();
      if (vFile != null) {
        text = vFile.getNameWithoutExtension();
      }
      // We must not look for file references with the file language's case-sensitivity, 
      // since case-sensitivity of the references themselves depends either on file system 
      // or on the rules of the language of reference
      caseSensitive = false;
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
      p.getOptimizer().searchWord(text, searchScope, caseSensitive, refElement);
    }
  }
}