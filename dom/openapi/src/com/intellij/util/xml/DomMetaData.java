/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.util.xml;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.meta.PsiWritableMetaData;
import com.intellij.psi.meta.PsiPresentableMetaData;
import com.intellij.psi.meta.PsiMetaData;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;

/**
 * @author peter
 */
public class DomMetaData<T extends DomElement> implements PsiWritableMetaData, PsiPresentableMetaData, PsiMetaData {
  private T myElement;
  private GenericDomValue myNameElement;

  public final PsiElement getDeclaration() {
    return myElement.getXmlTag();
  }

  public T getElement() {
    return myElement;
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
    myElement = (T) DomManager.getDomManager(element.getProject()).getDomElement((XmlTag)element);
    assert myElement != null : element;
    myNameElement = getNameElement(myElement);
    assert myNameElement != null : element;
  }

  protected GenericDomValue getNameElement(final T t) {
    return myElement.getGenericInfo().getNameDomElement(t);
  }

  public Object[] getDependences() {
    final PsiElement declaration = getDeclaration();
    if (myElement != null && myElement.isValid()) {
      return new Object[]{myElement.getRoot(), declaration};
    }
    return new Object[]{declaration};
  }

  public void setName(String name) throws IncorrectOperationException {
    myNameElement.setStringValue(name);
  }

  public String getTypeName() {
    return ElementPresentationManager.getTypeName(myElement.getClass());
  }

  public Icon getIcon() {
    return ElementPresentationManager.getIcon(myElement);
  }
}
