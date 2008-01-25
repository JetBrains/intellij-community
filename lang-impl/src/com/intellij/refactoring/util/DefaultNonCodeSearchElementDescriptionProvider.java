package com.intellij.refactoring.util;

import com.intellij.psi.*;
import com.intellij.psi.impl.file.PsiDirectoryFactory;
import com.intellij.psi.meta.PsiMetaOwner;
import com.intellij.psi.meta.PsiMetaData;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public class DefaultNonCodeSearchElementDescriptionProvider implements ElementDescriptionProvider {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.util.DefaultNonCodeSearchElementDescriptionProvider");

  public static final DefaultNonCodeSearchElementDescriptionProvider INSTANCE = new DefaultNonCodeSearchElementDescriptionProvider();
  
  public String getElementDescription(final PsiElement element, @Nullable final ElementDescriptionLocation location) {
    if (!(location instanceof NonCodeSearchDescriptionLocation)) return null;
    final NonCodeSearchDescriptionLocation ncdLocation = (NonCodeSearchDescriptionLocation)location;

    if (element instanceof PsiDirectory) {
      if (ncdLocation.isNonJava()) {
        final String qName = PsiDirectoryFactory.getInstance(element.getProject()).getQualifiedName((PsiDirectory)element, false);
        if (qName.length() > 0) return qName;
        return null;
      }
      return ((PsiDirectory) element).getName();
    }

    if (ncdLocation.isNonJava()) return null;
    if (element instanceof PsiMetaOwner) {
      final PsiMetaOwner psiMetaOwner = (PsiMetaOwner)element;
      final PsiMetaData metaData = psiMetaOwner.getMetaData();
      if (metaData != null) {
        return metaData.getName();
      }
    }
    if (element instanceof PsiNamedElement) {
      return ((PsiNamedElement)element).getName();
    }
    else {
      LOG.error("Unknown element type: " + element);
      return null;
    }
  }
}
