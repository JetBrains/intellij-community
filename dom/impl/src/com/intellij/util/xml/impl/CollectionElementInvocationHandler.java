/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml.impl;

import com.intellij.psi.xml.XmlTag;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.events.CollectionElementRemovedEvent;
import com.intellij.openapi.util.Factory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Type;
import java.lang.annotation.Annotation;
import java.util.Arrays;

/**
 * @author peter
 */
public class CollectionElementInvocationHandler extends DomInvocationHandler{

  public CollectionElementInvocationHandler(final Type type,
                                            @NotNull final XmlTag tag,
                                            final DomInvocationHandler parent) {
    super(type, tag, parent, tag.getName(), parent.getManager());
  }

  protected final XmlTag setXmlTag(final XmlTag tag) throws IncorrectOperationException {
    throw new UnsupportedOperationException("CollectionElementInvocationHandler.setXmlTag() shouldn't be called");
  }

  public final void undefineInternal() {
    final DomElement parent = getParent();
    final XmlTag tag = getXmlTag();
    detach(true);
    deleteTag(tag);
    getManager().fireEvent(new CollectionElementRemovedEvent(getProxy(), parent, getXmlElementName()));
  }

  @Nullable
  public final <T extends Annotation> T getAnnotation(Class<T> annotationClass) {
    return getParentHandler().getGenericInfo().getCollectionChildDescription(getXmlElementName()).getAnnotation(annotationClass);
  }

  public <T extends DomElement> T createStableCopy() {
    final DomElement parent = getParent();
    final DomElement parentCopy = parent.createStableCopy();
    final String tagName = getXmlElementName();
    final int index = Arrays.asList(parent.getXmlTag().findSubTags(tagName)).indexOf(getXmlTag());
    return getManager().createStableValue(new Factory<T>() {
      public T create() {
        if (!parentCopy.isValid()) return null;
        final XmlTag tag = parentCopy.getXmlTag();
        if (tag == null) return null;
        final XmlTag[] subTags = tag.findSubTags(tagName);
        if (subTags.length <= index) {
          return null;
        }
        return (T)getManager().getDomElement(subTags[index]);
      }
    });
  }
}
