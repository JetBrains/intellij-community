/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.util.xml.impl;

import com.intellij.psi.xml.XmlTag;
import com.intellij.util.xml.DomElement;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.THashMap;

import java.util.*;

/**
 * @author peter
*/
class GetCompositeCollectionInvocation implements Invocation {
  private final Set<CollectionChildDescriptionImpl> myQnames;

  public GetCompositeCollectionInvocation(final Set<CollectionChildDescriptionImpl> qnames) {
    myQnames = qnames;
  }

  public Object invoke(final DomInvocationHandler<?> handler, final Object[] args) throws Throwable {
    handler.checkIsValid();
    Map<XmlTag,DomElement> map = new THashMap<XmlTag, DomElement>();
    for (final CollectionChildDescriptionImpl qname : myQnames) {
      for (DomElement element : handler.getCollectionChildren(qname, qname.getTagsGetter())) {
        map.put(element.getXmlTag(), element);
      }
    }
    final XmlTag tag = handler.getXmlTag();
    if (tag == null) return Collections.emptyList();

    final List<DomElement> list = new ArrayList<DomElement>();
    for (final XmlTag subTag : tag.getSubTags()) {
      ContainerUtil.addIfNotNull(map.get(subTag), list);
    }
    return list;
  }
}
