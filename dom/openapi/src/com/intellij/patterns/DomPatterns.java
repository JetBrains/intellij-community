/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.patterns;

import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomManager;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public class DomPatterns {

  public static <T extends DomElement> DomElementPattern.Capture<T> domElement(Class<T> aClass) {
    return new DomElementPattern.Capture<T>(aClass);
  }

  public static DomElementPattern.Capture<DomElement> domElement() {
    return domElement(DomElement.class);
  }

  public static GenericDomValuePattern<?> genericDomValue() {
    return new GenericDomValuePattern();
  }

  public static <T> GenericDomValuePattern<T> genericDomValue(ElementPattern<?> valuePattern) {
    return ((GenericDomValuePattern)genericDomValue()).withValue(valuePattern);
  }

  public static <T> GenericDomValuePattern<T> genericDomValue(Class<T> aClass) {
    return new GenericDomValuePattern<T>(aClass);
  }

  public static XmlElementPattern.Capture withDom(final ElementPattern<? extends DomElement> pattern) {
    return new XmlElementPattern.Capture().with(new PatternCondition<XmlElement>("tagWithDom") {
      public boolean accepts(@NotNull final XmlElement xmlElement, final ProcessingContext context) {
        final DomManager manager = DomManager.getDomManager(xmlElement.getProject());
        if (xmlElement instanceof XmlAttribute) {
          return pattern.getCondition().accepts(manager.getDomElement((XmlAttribute)xmlElement), context);
        }
        return xmlElement instanceof XmlTag && pattern.getCondition().accepts(manager.getDomElement((XmlTag)xmlElement), context);
      }
    });
  }

  public static XmlTagPattern.Capture tagWithDom(String tagName, Class<? extends DomElement> aClass) {
    return tagWithDom(tagName, domElement(aClass));
  }

  public static XmlTagPattern.Capture tagWithDom(String tagName, ElementPattern<? extends DomElement> domPattern) {
    return XmlPatterns.xmlTag().withLocalName(tagName).and(withDom(domPattern));
  }
}
