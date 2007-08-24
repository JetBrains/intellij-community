/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml.impl;

import com.intellij.openapi.util.Factory;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.events.CollectionElementRemovedEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Type;
import java.util.List;

/**
 * @author peter
 */
public class CollectionElementInvocationHandler extends DomInvocationHandler<AbstractDomChildDescriptionImpl>{
  private final String myNamespace;

  public CollectionElementInvocationHandler(final Type type, @NotNull final XmlTag tag,
                                            final AbstractCollectionChildDescription description,
                                            final DomInvocationHandler parent) {
    super(type, tag, parent, description.createEvaluatedXmlName(parent, tag), (AbstractDomChildDescriptionImpl)description, parent.getManager());
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
    if (tag == null) return;

    final String namespace = tag.getNamespace();
    detach(true);
    deleteTag(tag);
    getManager().fireEvent(new CollectionElementRemovedEvent(getProxy(), parent, getXmlElementName(), namespace));
  }

  public DomElement createPathStableCopy() {
    final AbstractDomChildDescriptionImpl description = getChildDescription();
    final DomElement parent = getParent();
    assert parent != null;
    final DomElement parentCopy = parent.createStableCopy();
    final int index = description.getValues(parent).indexOf(getProxy());
    return getManager().createStableValue(new Factory<DomElement>() {
      @Nullable
      public DomElement create() {
        if (parentCopy.isValid()) {
          final List<? extends DomElement> list = description.getValues(parentCopy);
          if (list.size() > index) {
            return list.get(index);
          }
        }
        return null;
      }
    });
  }
}
