/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.util.xml.impl;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.xml.XmlTag;

import java.util.Set;
import java.lang.reflect.Type;

/**
 * @author peter
*/
class AddToCompositeCollectionInvocation implements Invocation {
  private final CollectionChildDescriptionImpl myMainDescription;
  private final Set<CollectionChildDescriptionImpl> myQnames;
  private final Type myType;

  public AddToCompositeCollectionInvocation(final CollectionChildDescriptionImpl tagName, final Set<CollectionChildDescriptionImpl> qnames, final Type type) {
    myMainDescription = tagName;
    myQnames = qnames;
    myType = type;
  }

  public Object invoke(final DomInvocationHandler handler, final Object[] args) throws Throwable {
    final VirtualFile virtualFile = handler.getFile().getVirtualFile();
    if (virtualFile != null && !virtualFile.isWritable()) {
      VirtualFileManager.getInstance().fireReadOnlyModificationAttempt(virtualFile);
      return null;
    }

    for (final CollectionChildDescriptionImpl qname : myQnames) {
      handler.checkInitialized(qname);
    }

    final XmlTag tag = handler.ensureTagExists();
    int index = args != null && args.length == 1 ? (Integer)args[0] : Integer.MAX_VALUE;

    XmlTag lastTag = null;
    int i = 0;
    final XmlTag[] tags = tag.getSubTags();
    for (final XmlTag subTag : tags) {
      if (i == index) break;
      if (DomImplUtil.containsTagName(myQnames, subTag, handler)) {
        final DomInvocationHandler element = DomManagerImpl.getCachedElement(subTag);
        if (element != null) {
          lastTag = subTag;
          i++;
        }
      }
    }
    final DomManagerImpl manager = handler.getManager();
    final boolean b = manager.setChanging(true);
    try {
      final EvaluatedXmlName evaluatedXmlName = handler.createEvaluatedXmlName(myMainDescription.getXmlName());
      final XmlTag emptyTag = handler.createChildTag(evaluatedXmlName);
      final XmlTag newTag;
      if (lastTag == null) {
        if (tags.length == 0) {
          newTag = (XmlTag)tag.add(emptyTag);
        }
        else {
          newTag = (XmlTag)tag.addBefore(emptyTag, tags[0]);
        }
      }
      else {
        newTag = (XmlTag)tag.addAfter(emptyTag, lastTag);
      }

      return new CollectionElementInvocationHandler(myType, evaluatedXmlName, newTag, myMainDescription, handler).getProxy();
    }
    finally {
      manager.setChanging(b);
    }
  }


}
