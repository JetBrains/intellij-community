/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml.impl;

import com.intellij.psi.xml.XmlTag;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.XmlName;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author peter
 */
public class GetCollectionChildInvocation implements Invocation {
  private final XmlName myQname;

  public GetCollectionChildInvocation(final XmlName qname) {
    myQname = qname;
  }

  public Object invoke(final DomInvocationHandler handler, final Object[] args) throws Throwable {
    assert handler.isValid() : "dom element is not valid";
    XmlTag tag = handler.getXmlTag();
    if (tag == null) return Collections.emptyList();

    final EvaluatedXmlName xmlName = handler.createEvaluatedXmlName(myQname);
    handler.checkInitialized(xmlName);
    final List<XmlTag> subTags = DomImplUtil.findSubTags(tag, xmlName, handler);
    if (subTags.isEmpty()) return Collections.emptyList();

    List<DomElement> elements = new ArrayList<DomElement>(subTags.size());
    for (XmlTag subTag : subTags) {
      final DomInvocationHandler element = DomManagerImpl.getCachedElement(subTag);
      if (element != null) {
        elements.add(element.getProxy());
      }
    }
    return Collections.unmodifiableList(elements);
  }
}
