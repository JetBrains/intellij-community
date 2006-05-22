/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml.impl;

import com.intellij.psi.xml.XmlTag;
import com.intellij.util.xml.DomElement;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author peter
 */
public class GetCollectionChildInvocation implements Invocation {
  private final String myQname;

  public GetCollectionChildInvocation(final String qname) {
    myQname = qname;
  }

  public Object invoke(final DomInvocationHandler handler, final Object[] args) throws Throwable {
    assert handler.isValid();
    XmlTag tag = handler.getXmlTag();
    if (tag == null) return Collections.emptyList();

    handler.checkInitialized(myQname);
    final XmlTag[] subTags = tag.findSubTags(myQname);
    if (subTags.length == 0) return Collections.emptyList();

    List<DomElement> elements = new ArrayList<DomElement>(subTags.length);
    for (XmlTag subTag : subTags) {
      final DomInvocationHandler element = DomManagerImpl.getCachedElement(subTag);
      if (element != null) {
        elements.add(element.getProxy());
      }
    }
    return Collections.unmodifiableList(elements);
  }
}
