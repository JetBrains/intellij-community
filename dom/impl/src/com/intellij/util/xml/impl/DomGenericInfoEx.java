/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.util.xml.impl;

import com.intellij.util.xml.JavaMethod;
import com.intellij.util.xml.reflect.DomGenericInfo;
import com.intellij.util.xml.reflect.AbstractDomChildrenDescription;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author peter
 */
public abstract class DomGenericInfoEx implements DomGenericInfo {

  public abstract void checkInitialized();

  public abstract Invocation createInvocation(final JavaMethod method);

  @NotNull
  public abstract List<AttributeChildDescriptionImpl> getAttributeChildrenDescriptions();

  @Nullable
  public final AbstractDomChildrenDescription findChildrenDescription(DomInvocationHandler handler, final String localName, String namespace,
                                                               boolean attribute,
                                                               final String qName) {
    for (final AbstractDomChildrenDescription description : getChildrenDescriptions()) {
      if (description instanceof DomChildDescriptionImpl && description instanceof AttributeChildDescriptionImpl == attribute) {
        final EvaluatedXmlName xmlName = handler.createEvaluatedXmlName(((DomChildDescriptionImpl)description).getXmlName());
        if (DomImplUtil.isNameSuitable(xmlName, localName, qName, namespace, handler)) {
          return description;
        }
      }
    }
    return attribute ? null : getCustomNameChildrenDescription();
  }
}
