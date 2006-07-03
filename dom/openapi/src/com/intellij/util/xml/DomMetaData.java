/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.util.xml;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.meta.PsiWritableMetaData;
import com.intellij.psi.meta.PsiPresentableMetaData;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;

/**
 * @author peter
 */
public class DomMetaData implements PsiWritableMetaData, PsiPresentableMetaData {
  private DomElement myElement;
  private GenericDomValue myNameElement;

  public final PsiElement getDeclaration() {
    return myElement.getXmlTag();
  }

  public boolean processDeclarations(PsiElement context, PsiScopeProcessor processor, PsiSubstitutor substitutor, PsiElement lastElement,
                                     PsiElement place) {
    return true;
  }

  @NonNls
  public String getName(PsiElement context) {
    return getName();
  }

  @NonNls
  public final String getName() {
    return myNameElement.getStringValue();
  }

  public void init(PsiElement element) {
    myElement = DomManager.getDomManager(element.getProject()).getDomElement((XmlTag)element);
    assert myElement != null : element;
    myNameElement = myElement.getGenericInfo().getNameDomElement(myElement);
    assert myNameElement != null : element;
  }

  public Object[] getDependences() {
    return new Object[]{getDeclaration()};
  }

  public void setName(String name) throws IncorrectOperationException {
    myNameElement.setStringValue(name);
  }

  public String getTypeName() {
    return ElementPresentationManager.getTypeName(myElement.getClass());
  }
}
