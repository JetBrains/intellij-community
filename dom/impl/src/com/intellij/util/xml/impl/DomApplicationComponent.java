/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.util.xml.impl;

import com.intellij.psi.PsiElement;
import com.intellij.psi.filters.ElementFilter;
import com.intellij.psi.meta.MetaDataRegistrar;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomManager;
import com.intellij.util.xml.DomMetaData;

/**
 * @author peter
 */
public class DomApplicationComponent {


  public DomApplicationComponent() {
    MetaDataRegistrar.getInstance().registerMetaData(new ElementFilter() {
      public boolean isAcceptable(Object element, PsiElement context) {
        if (element instanceof XmlTag) {
          final XmlTag tag = (XmlTag)element;
          final DomElement domElement = DomManager.getDomManager(tag.getProject()).getDomElement(tag);
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

}
