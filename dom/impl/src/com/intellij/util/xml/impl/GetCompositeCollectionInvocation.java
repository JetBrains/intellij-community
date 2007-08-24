/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.util.xml.impl;

import com.intellij.psi.xml.XmlTag;
import com.intellij.util.xml.DomElement;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * @author peter
*/
class GetCompositeCollectionInvocation implements Invocation {
  private final Set<CollectionChildDescriptionImpl> myQnames;

  public GetCompositeCollectionInvocation(final Set<CollectionChildDescriptionImpl> qnames) {
    myQnames = qnames;
  }

  public Object invoke(final DomInvocationHandler<?> handler, final Object[] args) throws Throwable {
    DomInvocationHandler.r.lock();
    try {
      for (final CollectionChildDescriptionImpl qname : myQnames) {
        handler._checkInitialized(qname);
      }
      final XmlTag tag = handler.getXmlTag();
      if (tag == null) return Collections.emptyList();

      final List<DomElement> list = new ArrayList<DomElement>();
      for (final XmlTag subTag : tag.getSubTags()) {
        if (DomImplUtil.containsTagName(myQnames, subTag, handler)) {
          final DomInvocationHandler element = DomManagerImpl.getCachedElement(subTag);
          if (element != null) {
            list.add(element.getProxy());
          }
        }
      }
      return list;
    }
    finally {
      DomInvocationHandler.r.unlock();
    }
  }
}
