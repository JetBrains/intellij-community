/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.util.xml.impl;

import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.psi.PsiElement;
import com.intellij.psi.filters.ElementFilter;
import com.intellij.psi.impl.meta.MetaRegistry;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.xml.DomMetaData;
import com.intellij.util.xml.DomElement;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public class DomApplicationComponent implements ApplicationComponent {


  public DomApplicationComponent() {
    MetaRegistry.addMetadataBinding(new ElementFilter() {
      public boolean isAcceptable(Object element, PsiElement context) {
        if (element instanceof XmlTag) {
          final XmlTag tag = (XmlTag)element;
          final DomElement domElement = DomManagerImpl.getDomManager(tag.getProject()).getDomElement(tag);
          if (domElement != null) {
            return domElement.getGenericInfo().getNameDomElement(domElement) != null;
          }
        }
        return false;
      }

      public boolean isClassAcceptable(Class hintClass) {
        return XmlTag.class.isAssignableFrom(hintClass);
      }
    }, DomMetaData.class);

  }

  @NonNls
  @NotNull
  public String getComponentName() {
    return getClass().getName();
  }

  public void initComponent() {

  }

  public void disposeComponent() {

  }
}
