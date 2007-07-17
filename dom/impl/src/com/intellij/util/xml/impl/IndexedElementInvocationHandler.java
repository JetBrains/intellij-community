/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Factory;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.reflect.DomFixedChildDescription;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.List;

/**
 * @author peter
 */
public class IndexedElementInvocationHandler extends DomInvocationHandler{
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

  public boolean isValid() {
    return super.isValid() && getParentHandler().isValid();
  }

  protected XmlTag setEmptyXmlTag() {
    final DomInvocationHandler parent = getParentHandler();
    final FixedChildDescriptionImpl description = (FixedChildDescriptionImpl)getChildDescription();
    parent.createFixedChildrenTags(getXmlName(), description, myIndex);
    final XmlTag[] newTag = new XmlTag[1];
    getManager().runChange(new Runnable() {
      public void run() {
        try {
          final XmlTag parentTag = parent.getXmlTag();
          newTag[0] = (XmlTag)parentTag.add(parent.createChildTag(getXmlName()));
          if (getParentHandler().getFixedChildrenClass(description) != null) {
            getManager().getTypeChooserManager().getTypeChooser(getChildDescription().getType()).distinguishTag(newTag[0], getDomElementType());
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
    parent.checkInitialized(getChildDescription());

    final int totalCount = ((FixedChildDescriptionImpl)getChildDescription()).getCount();

    final List<XmlTag> subTags = DomImplUtil.findSubTags(parentTag, xmlElementName, this);
    if (subTags.size() <= myIndex) {
      return;
    }

    final boolean changing = getManager().setChanging(true);
    try {
      XmlTag tag = getXmlTag();
      assert tag != null;
      detach(false);
      if (totalCount == myIndex + 1 && subTags.size() >= myIndex + 1) {
        for (int i = myIndex; i < subTags.size(); i++) {
          subTags.get(i).delete();
        }
      }
      else if (subTags.size() == myIndex + 1) {
        tag.delete();
      } else {
        attach((XmlTag) tag.replace(parent.createChildTag(getXmlName())));
      }
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    } finally {
      getManager().setChanging(changing);
    }
    detachChildren();
    fireUndefinedEvent();
  }

  public final <T extends Annotation> T getAnnotation(Class<T> annotationClass) {
    final T annotation = ((FixedChildDescriptionImpl)getChildDescription()).getAnnotation(myIndex, annotationClass);
    if (annotation != null) return annotation;

    return getRawType().getAnnotation(annotationClass);
  }

  public final DomElement createPathStableCopy() {
    final DomFixedChildDescription description = (DomFixedChildDescription)getChildDescription();
    final DomElement parentCopy = getParent().createStableCopy();
    return getManager().createStableValue(new Factory<DomElement>() {
      public DomElement create() {
        return parentCopy.isValid() ? description.getValues(parentCopy).get(myIndex) : null;
      }
    });
  }

}
