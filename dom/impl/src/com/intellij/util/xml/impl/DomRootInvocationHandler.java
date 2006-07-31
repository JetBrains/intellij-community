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
import com.intellij.util.xml.DomFileElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.annotation.Annotation;

/**
 * @author peter
 */
public class DomRootInvocationHandler extends DomInvocationHandler {
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.xml.impl.DomRootInvocationHandler");
  private DomFileElementImpl<?> myParent;

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
        detachChildren();
        fireUndefinedEvent();
      }
    }
    catch (Exception e) {
      LOG.error(e);
    }
  }

  public boolean isValid() {
    return super.isValid() && myParent.isValid();
  }

  @Nullable
  public <T extends Annotation> T getAnnotation(Class<T> annotationClass) {
    return null;
  }

  public <T extends DomElement> DomFileElementImpl<T> getRoot() {
    return isValid() ? (DomFileElementImpl<T>)myParent : null;
  }

  public DomElement getParent() {
    return isValid() ? myParent : null;
  }

  public <T extends DomElement> T createStableCopy() {
    final DomFileElement stableCopy = myParent.createStableCopy();
    return getManager().createStableValue(new Factory<T>() {
      public T create() {
        return stableCopy.isValid() ? (T) stableCopy.getRootElement() : null;
      }
    });
  }

  protected XmlTag setXmlTag(final XmlTag tag) throws IncorrectOperationException {
    final XmlTag[] result = new XmlTag[]{null};
    getManager().runChange(new Runnable() {
      public void run() {
        try {
          result[0] = ((XmlDocument)getFile().getDocument().replace(((XmlFile)tag.getContainingFile()).getDocument())).getRootTag();
        }
        catch (IncorrectOperationException e) {
          LOG.error(e);
        }
      }
    });
    return result[0];
  }

}
