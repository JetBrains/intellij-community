/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Factory;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.xml.DomElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.annotation.Annotation;

/**
 * @author peter
 */
public class DomRootInvocationHandler extends DomInvocationHandler {
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.xml.impl.DomRootInvocationHandler");
  private DomFileElementImpl myParent;

  public DomRootInvocationHandler(final Class aClass,
                                  final XmlTag tag,
                                  final DomFileElementImpl fileElement,
                                  @NotNull final String tagName
  ) {
    super(aClass, tag, null, tagName, fileElement.getManager());
    myParent = fileElement;
  }

  public void undefineInternal() {
    try {
      final XmlTag tag = getXmlTag();
      if (tag != null) {
        deleteTag(tag);
        undefineChildren();
        fireUndefinedEvent();
      }
    }
    catch (Exception e) {
      LOG.error(e);
    }
  }

  @Nullable
  public <T extends Annotation> T getAnnotation(Class<T> annotationClass) {
    return null;
  }

  public <T extends DomElement> DomFileElementImpl<T> getRoot() {
    return isValid() ? myParent : null;
  }

  public DomElement getParent() {
    return isValid() ? myParent : null;
  }

  public <T extends DomElement> T createStableCopy() {
    return getManager().createStableValue(new Factory<T>() {
      public T create() {
        return (T)myParent.getRootElement();
      }
    });
  }

  protected XmlTag setXmlTag(final XmlTag tag) throws IncorrectOperationException {
    return ((XmlDocument)getFile().getDocument().replace(((XmlFile)tag.getContainingFile()).getDocument())).getRootTag();
  }

}
