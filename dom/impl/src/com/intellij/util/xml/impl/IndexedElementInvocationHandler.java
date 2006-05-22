/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Factory;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.xml.ClassChooserManager;
import com.intellij.util.xml.Converter;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomUtil;
import com.intellij.util.xml.reflect.DomFixedChildDescription;

import java.lang.reflect.Type;

/**
 * @author peter
 */
public class IndexedElementInvocationHandler extends DomInvocationHandler{
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.xml.impl.IndexedElementInvocationHandler");
  private final int myIndex;
  private final boolean myIndicator;

  public IndexedElementInvocationHandler(final Type aClass,
                                         final XmlTag tag,
                                         final DomInvocationHandler parent,
                                         final String tagName,
                                         final int index,
                                         final Converter genericConverter,
                                         final boolean indicator) {
    super(aClass, tag, parent, tagName, parent.getManager(), genericConverter);
    myIndex = index;
    myIndicator = indicator;
  }

  final boolean isIndicator() {
    return myIndicator;
  }

  public final int getIndex() {
    return myIndex;
  }

  protected XmlTag setXmlTag(final XmlTag tag) throws IncorrectOperationException, IllegalAccessException, InstantiationException {
    final DomInvocationHandler parent = getParentHandler();
    parent.createFixedChildrenTags(getXmlElementName(), myIndex);
    final XmlTag newTag = (XmlTag)parent.getXmlTag().add(tag);
    if (getParentHandler().getFixedChildrenClass(tag.getName()) != null) {
      final Type type = getParentHandler().getGenericInfo().getFixedChildDescription(getXmlElementName()).getType();
      ClassChooserManager.getClassChooser(DomUtil.getRawType(type)).distinguishTag(newTag, DomUtil.getRawType(getDomElementType()));
    }
    return newTag;
  }

  public void undefineInternal() {
    final DomInvocationHandler parent = getParentHandler();
    final XmlTag parentTag = parent.getXmlTag();
    if (parentTag == null) return;

    final String xmlElementName = getXmlElementName();
    parent.checkInitialized(xmlElementName);

    final int totalCount = parent.getGenericInfo().getFixedChildrenCount(xmlElementName);

    final XmlTag[] subTags = parentTag.findSubTags(xmlElementName);
    if (subTags.length <= myIndex) {
      return;
    }

    final boolean changing = getManager().setChanging(true);
    try {
      XmlTag tag = getXmlTag();
      assert tag != null;
      detach(false);
      if (totalCount == myIndex + 1 && subTags.length >= myIndex + 1) {
        for (int i = myIndex; i < subTags.length; i++) {
          subTags[i].delete();
        }
      }
      else if (subTags.length == myIndex + 1) {
        tag.delete();
      } else {
        attach((XmlTag) tag.replace(createEmptyTag()));
      }
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    } finally {
      getManager().setChanging(changing);
    }
    undefineChildren();
    fireUndefinedEvent();
  }

  public <T extends DomElement> T createStableCopy() {
    final DomElement parentCopy = getParent().createStableCopy();
    final DomFixedChildDescription description = parentCopy.getGenericInfo().getFixedChildDescription(getXmlElementName());
    return getManager().createStableValue(new Factory<T>() {
      public T create() {
        return (T)description.getValues(parentCopy).get(myIndex);
      }
    });
  }
}
