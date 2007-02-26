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
import com.intellij.util.xml.DomElementVisitor;
import com.intellij.util.xml.events.ElementDefinedEvent;
import com.intellij.util.xml.reflect.DomAttributeChildDescription;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Type;

/**
 * @author peter
 */
public class AttributeChildInvocationHandler extends DomInvocationHandler {
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.xml.impl.AttributeChildInvocationHandler");
  private boolean myWasDefined;

  protected AttributeChildInvocationHandler(final Type type,
                                            final XmlTag tag,
                                            final DomInvocationHandler parent,
                                            final EvaluatedXmlName attributeName,
                                            final DomManagerImpl manager) {
    super(type, tag, parent, attributeName, manager);
    myWasDefined = getXmlElement() != null;
  }

  public void acceptChildren(DomElementVisitor visitor) {
  }

  protected final XmlTag setEmptyXmlTag() {
    return ensureTagExists();
  }

  protected boolean isAttribute() {
    return true;
  }

  public final XmlAttribute getXmlElement() {
    final XmlTag tag = getXmlTag();
    return tag == null ? null : tag.getAttribute(getXmlElementName(), getXmlElementNamespace());
  }

  public final XmlAttribute ensureXmlElementExists() {
    XmlAttribute xmlAttribute = getXmlElement();
    if (xmlAttribute != null) return xmlAttribute;

    final DomManagerImpl manager = getManager();
    final boolean b = manager.setChanging(true);
    try {
      xmlAttribute = ensureTagExists().setAttribute(getXmlElementName(), getXmlElementNamespace(), "");
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
    final DomElement parentCopy = findCallerProxy(CREATE_STABLE_COPY_METHOD).getParent().createStableCopy();
    final DomAttributeChildDescription description = getChildDescription();
    return getManager().createStableValue(new Factory<T>() {
      public T create() {
        return parentCopy.isValid() ? (T) description.getValues(parentCopy).get(0) : null;
      }
    });
  }

  protected AttributeChildDescriptionImpl getChildDescription() {
    return getParentHandler().getGenericInfo().getAttributeChildDescription(getXmlName().getXmlName());
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

  protected final Invocation createSetValueInvocation(final Converter converter) {
    return new SetAttributeValueInvocation(converter);
  }

  protected final Invocation createGetValueInvocation(final Converter converter) {
    return new GetAttributeValueInvocation(converter);
  }

  public final void undefineInternal() {
    final XmlTag tag = getXmlTag();
    setDefined(false);
    setXmlTagToNull();
    if (tag != null) {
      getManager().runChange(new Runnable() {
        public void run() {
          try {
            tag.setAttribute(getXmlElementName(), getXmlElementNamespace(), null);
          }
          catch (IncorrectOperationException e) {
            LOG.error(e);
          }
        }
      });
      fireUndefinedEvent();
    }
  }

  @Nullable
  public final XmlTag getXmlTag() {
    return getParentHandler().getXmlTag();
  }

  public final XmlTag ensureTagExists() {
    return getParentHandler().ensureTagExists();
  }

}
