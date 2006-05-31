/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Factory;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.xml.Converter;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.events.ElementDefinedEvent;
import com.intellij.util.xml.reflect.DomAttributeChildDescription;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.lang.annotation.Annotation;

/**
 * @author peter
 */
public class AttributeChildInvocationHandler extends DomInvocationHandler {
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.xml.impl.AttributeChildInvocationHandler");
  private boolean myWasDefined;

  protected AttributeChildInvocationHandler(final Type type,
                                            final XmlTag tag,
                                            final DomInvocationHandler parent,
                                            final String attributeName,
                                            final DomManagerImpl manager) {
    super(type, tag, parent, attributeName, manager);
    if (tag != null && tag.getAttributeValue(attributeName) != null) {
      myWasDefined = true;
    }
  }

  protected final XmlTag setXmlTag(final XmlTag tag) {
    return tag;
  }

  protected boolean isAttribute() {
    return true;
  }

  public final XmlAttribute getXmlElement() {
    final XmlTag tag = getXmlTag();
    return tag == null ? null : tag.getAttribute(getXmlElementName(), null);
  }

  public final XmlAttribute ensureXmlElementExists() {
    XmlAttribute xmlAttribute = getXmlElement();
    if (xmlAttribute != null) return xmlAttribute;

    final DomManagerImpl manager = getManager();
    final boolean b = manager.setChanging(true);
    try {
      xmlAttribute = ensureTagExists().setAttribute(getXmlElementName(), "");
      manager.fireEvent(new ElementDefinedEvent(getProxy()));
      return xmlAttribute;
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
      return null;
    }
    finally {
      manager.setChanging(b);
    }
  }

  public <T extends DomElement> T createStableCopy() {
    final DomElement parentCopy = getParent().createStableCopy();
    final DomAttributeChildDescription description = getChildDescription();
    return getManager().createStableValue(new Factory<T>() {
      public T create() {
        return (T)description.getValues(parentCopy).get(0);
      }
    });
  }

  private AttributeChildDescriptionImpl getChildDescription() {
    return getParentHandler().getGenericInfo().getAttributeChildDescription(getXmlElementName());
  }

  protected final void cacheInTag(final XmlTag tag) {
  }

  public final boolean wasDefined() {
    return myWasDefined;
  }

  public final void setDefined(final boolean wasDefined) {
    myWasDefined = wasDefined;
  }

  protected final void removeFromCache() {
  }

  protected final Invocation createSetValueInvocation(final Converter converter, final Method method) {
    return new SetAttributeValueInvocation(converter, method);
  }

  protected final Invocation createGetValueInvocation(final Converter converter, final Method method) {
    return new GetAttributeValueInvocation(converter, method);
  }

  public final void undefineInternal() {
    final XmlTag tag = getXmlTag();
    setDefined(false);
    setXmlTagToNull();
    if (tag != null) {
      try {
        tag.setAttribute(getXmlElementName(), null);
      }
      catch (IncorrectOperationException e) {
        LOG.error(e);
      }
      fireUndefinedEvent();
    }
  }

  public <T extends Annotation> T getAnnotation(Class<T> annotationClass) {
    return getChildDescription().getAnnotation(annotationClass);
  }

  @Nullable
  public final XmlTag getXmlTag() {
    return getParentHandler().getXmlTag();
  }

  public final XmlTag ensureTagExists() {
    return getParentHandler().ensureTagExists();
  }

}
