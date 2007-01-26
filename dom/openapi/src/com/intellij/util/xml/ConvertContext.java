/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.openapi.module.Module;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author peter
 */
public abstract class ConvertContext {

  @NotNull
  public abstract DomElement getInvocationElement();

  @Nullable
  public abstract PsiClass findClass(String name, @Nullable final GlobalSearchScope searchScope);

  @Nullable
  public abstract XmlTag getTag();

  @Nullable
  public abstract XmlElement getXmlElement();

  @Nullable
  public XmlElement getReferenceXmlElement() {
    final XmlElement element = getXmlElement();
    if (element instanceof XmlTag) {
      return element;
    }
    if (element instanceof XmlAttribute) {
      return ((XmlAttribute)element).getValueElement();
    }
    return null;
  }

  @NotNull
  public abstract XmlFile getFile();

  public abstract Module getModule();
  
  public abstract PsiManager getPsiManager();
}
