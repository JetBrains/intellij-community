/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.patterns;

import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomManager;
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

  public static <T> GenericDomValuePattern<T> genericDomValue(ElementPattern valuePattern) {
    return ((GenericDomValuePattern)genericDomValue()).withValue(valuePattern);
  }

  public static <T> GenericDomValuePattern<T> genericDomValue(Class<T> aClass) {
    return new GenericDomValuePattern<T>(aClass);
  }

  public static XmlElementPattern.Capture withDom(final ElementPattern pattern) {
    return new XmlElementPattern.Capture().with(new PatternCondition<XmlElement>("withDom") {
      public boolean accepts(@NotNull final XmlElement xmlElement, final MatchingContext matchingContext, @NotNull final TraverseContext traverseContext) {
        final DomManager manager = DomManager.getDomManager(xmlElement.getProject());
        if (xmlElement instanceof XmlAttribute) {
          return pattern.getCondition().accepts(manager.getDomElement((XmlAttribute)xmlElement), matchingContext, traverseContext);
        }
        if (xmlElement instanceof XmlTag) {
          return pattern.getCondition().accepts(manager.getDomElement((XmlTag)xmlElement), matchingContext, traverseContext);
        }
        return false;
      }
    });
  }


}
