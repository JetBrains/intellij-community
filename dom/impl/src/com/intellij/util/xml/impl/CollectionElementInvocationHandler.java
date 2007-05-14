/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml.impl;

import com.intellij.openapi.util.Factory;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.xml.AnnotatedElement;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.events.CollectionElementRemovedEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Type;
import java.util.List;

/**
 * @author peter
 */
public class CollectionElementInvocationHandler extends DomInvocationHandler{
  private final String myNamespace;

  public CollectionElementInvocationHandler(final Type type,
                                            final EvaluatedXmlName name,
                                            @NotNull final XmlTag tag,
                                            final DomInvocationHandler parent) {
    super(type, tag, parent, name, parent.getManager());
    myNamespace = tag.getNamespace();
  }

  protected final XmlTag setEmptyXmlTag() {
    throw new UnsupportedOperationException("CollectionElementInvocationHandler.setXmlTag() shouldn't be called;" +
                                            "\nparent=" + getParent() + ";\n" +
                                            "xmlElementName=" + getXmlElementName());
  }

  public String getNamespace() {
    return myNamespace;
  }

  public boolean isValid() {
    if (!super.isValid()) {
      return false;
    }
    final XmlTag tag = getXmlTag();
    if (tag == null || !tag.isValid()) {
      detach(true);
      return false;
    }
    return true;
  }

  public final void undefineInternal() {
    final DomElement parent = getParent();
    final XmlTag tag = getXmlTag();
    final String namespace = tag.getNamespace();
    detach(true);
    deleteTag(tag);
    getManager().fireEvent(new CollectionElementRemovedEvent(getProxy(), parent, getXmlElementName(), namespace));
  }

  @Nullable
  protected AnnotatedElement getChildDescription() {
    return getParentHandler().getGenericInfo().getCollectionChildDescription(getXmlName().getXmlName());
  }

  public <T extends DomElement> T createStableCopy() {
    final DomElement parent = getParent();
    final DomElement parentCopy = parent.createStableCopy();
    final EvaluatedXmlName tagName = getXmlName();
    final int index = DomImplUtil.findSubTags(parent.getXmlTag(), tagName, this).indexOf(getXmlTag());
    return getManager().createStableValue(new Factory<T>() {
      @Nullable
      public T create() {
        if (!parentCopy.isValid()) return null;
        final XmlTag tag = parentCopy.getXmlTag();
        if (tag == null) return null;
        final List<XmlTag> subTags = DomImplUtil.findSubTags(tag, tagName, DomManagerImpl.getDomInvocationHandler(parentCopy));
        if (subTags.size() <= index) {
          return null;
        }
        return (T)getManager().getDomElement(subTags.get(index));
      }
    });
  }
}
