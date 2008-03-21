/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Factory;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlElement;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.reflect.DomFixedChildDescription;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.List;

/**
 * @author peter
 */
public class IndexedElementInvocationHandler extends DomInvocationHandler<FixedChildDescriptionImpl>{
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.xml.impl.IndexedElementInvocationHandler");
  private final int myIndex;

  public IndexedElementInvocationHandler(final Type aClass,
                                         final XmlTag tag,
                                         final DomInvocationHandler parent,
                                         final EvaluatedXmlName tagName,
                                         final FixedChildDescriptionImpl description,
                                         final int index) {
    super(aClass, tag, parent, tagName, description, parent.getManager());
    myIndex = index;
  }

  public boolean equals(final Object obj) {
    if (!(obj instanceof IndexedElementInvocationHandler)) return false;

    final IndexedElementInvocationHandler handler = (IndexedElementInvocationHandler)obj;
    if (!getParentHandler().equals(handler.getParentHandler()) || !getChildDescription().equals(handler.getChildDescription())) {
      return false;
    }

    final XmlTag tag = getXmlTag();
    final XmlTag herTag = handler.getXmlTag();
    if (tag != null) {
      return tag.equals(herTag);
    }
    if (herTag != null) return false;

    return myIndex == handler.myIndex;
  }

  public int hashCode() {
    return getParentHandler().hashCode() * 239 + getChildDescription().hashCode() * 42;
  }

  protected XmlElement recomputeXmlElement() {
    final DomInvocationHandler handler = getParentHandler();
    if (!handler.isValid()) return null;

    final XmlTag tag = handler.getXmlTag();
    if (tag == null) return null;

    final List<XmlTag> tags = DomImplUtil.findSubTags(tag, getXmlName(), getFile());
    if (tags.size() <= myIndex) return null;

    return tags.get(myIndex);
  }

  protected XmlTag setEmptyXmlTag() {
    final DomInvocationHandler parent = getParentHandler();
    final FixedChildDescriptionImpl description = getChildDescription();
    final XmlFile xmlFile = getFile();
    parent.createFixedChildrenTags(getXmlName(), description, myIndex);
    final List<XmlTag> tags = DomImplUtil.findSubTags(parent.getXmlTag(), getXmlName(), xmlFile);
    if (tags.size() > myIndex) {
      return tags.get(myIndex);
    }

    final XmlTag[] newTag = new XmlTag[1];
    getManager().runChange(new Runnable() {
      public void run() {
        try {
          final XmlTag parentTag = parent.getXmlTag();
          newTag[0] = (XmlTag)parentTag.add(parent.createChildTag(getXmlName()));
          if (getParentHandler().getFixedChildrenClass(description) != null) {
            getManager().getTypeChooserManager().getTypeChooser(description.getType()).distinguishTag(newTag[0], getDomElementType());
          }
        }
        catch (IncorrectOperationException e) {
          LOG.error(e);
        }
      }
    });
    return newTag[0];
  }

  public void undefineInternal() {
    final DomInvocationHandler parent = getParentHandler();
    final XmlTag parentTag = parent.getXmlTag();
    if (parentTag == null) return;

    final EvaluatedXmlName xmlElementName = getXmlName();
    final FixedChildDescriptionImpl description = getChildDescription();

    final int totalCount = description.getCount();

    final List<XmlTag> subTags = DomImplUtil.findSubTags(parentTag, xmlElementName, getFile());
    if (subTags.size() <= myIndex) {
      return;
    }

    XmlTag tag = getXmlTag();
    if (tag == null) return;

    final boolean changing = getManager().setChanging(true);
    try {
      if (totalCount == myIndex + 1 && subTags.size() >= myIndex + 1) {
        for (int i = myIndex; i < subTags.size(); i++) {
          subTags.get(i).delete();
        }
        detach();
      }
      else if (subTags.size() == myIndex + 1) {
        tag.delete();
        detach();
      } else {
        setXmlElement((XmlTag) tag.replace(parent.createChildTag(getXmlName())));
      }
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    } finally {
      getManager().setChanging(changing);
    }
    fireUndefinedEvent();
  }

  public final <T extends Annotation> T getAnnotation(Class<T> annotationClass) {
    final T annotation = getChildDescription().getAnnotation(myIndex, annotationClass);
    if (annotation != null) return annotation;

    return getRawType().getAnnotation(annotationClass);
  }

  public final DomElement createPathStableCopy() {
    final DomFixedChildDescription description = getChildDescription();
    final DomElement parentCopy = getParent().createStableCopy();
    return getManager().createStableValue(new Factory<DomElement>() {
      public DomElement create() {
        return parentCopy.isValid() ? description.getValues(parentCopy).get(myIndex) : null;
      }
    });
  }

}
