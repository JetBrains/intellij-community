/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Factory;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomElementVisitor;
import com.intellij.util.xml.events.ElementChangedEvent;
import com.intellij.util.xml.events.ElementDefinedEvent;
import com.intellij.xml.util.XmlStringUtil;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Type;

/**
 * @author peter
 */
public class AttributeChildInvocationHandler extends DomInvocationHandler<AttributeChildDescriptionImpl> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.xml.impl.AttributeChildInvocationHandler");
  private XmlAttribute myAttribute;

  protected AttributeChildInvocationHandler(final Type type,
                                            final XmlTag tag,
                                            final DomInvocationHandler parent,
                                            final EvaluatedXmlName attributeName,
                                            final AttributeChildDescriptionImpl description,
                                            final DomManagerImpl manager,
                                            final XmlAttribute attribute) {
    super(type, tag, parent, attributeName, description, manager);
    myAttribute = attribute;
  }

  public void acceptChildren(DomElementVisitor visitor) {
  }

  protected final XmlTag setEmptyXmlTag() {
    return ensureTagExists();
  }

  public boolean isValid() {
    return myAttribute == null ? getParentHandler().isValid() :  myAttribute.isValid();
  }

  protected boolean isAttribute() {
    return true;
  }

  public final XmlAttribute getXmlElement() {
    return myAttribute;
  }

  public boolean equals(final Object obj) {
    if (!(obj instanceof AttributeChildInvocationHandler)) return false;

    final DomInvocationHandler handler = (DomInvocationHandler)obj;
    return getParentHandler().equals(handler.getParentHandler()) && getChildDescription().equals(handler.getChildDescription());
  }

  public int hashCode() {
    return getParentHandler().hashCode() * 239 + getChildDescription().hashCode() * 42;
  }

  public final XmlAttribute ensureXmlElementExists() {
    if (myAttribute != null) return myAttribute;

    final DomManagerImpl manager = getManager();
    final boolean b = manager.setChanging(true);
    try {
      myAttribute = ensureTagExists().setAttribute(getXmlElementName(), getXmlElementNamespace(), "");
      getManager().cacheAttribute(myAttribute, this);
      manager.fireEvent(new ElementDefinedEvent(getProxy()));
      return myAttribute;
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
    return getManager().createStableValue(new Factory<T>() {
      public T create() {
        return parentCopy.isValid() ? (T) getChildDescription().getValues(parentCopy).get(0) : null;
      }
    });
  }

  protected final void cacheInTag(final XmlTag tag) {
  }

  protected final void removeFromCache() {
  }

  public final void undefineInternal() {
    final XmlTag tag = getXmlTag();
    w.lock();
    try {
      setXmlTag(null);
    }
    finally {
      w.unlock();
    }
    if (tag != null) {
      getManager().runChange(new Runnable() {
        public void run() {
          try {
            myAttribute = tag.setAttribute(getXmlElementName(), getXmlElementNamespace(), null);
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

  @Nullable
  protected String getValue() {
    if (myAttribute != null) {
      final XmlAttributeValue value = myAttribute.getValueElement();
      if (value != null && value.getTextLength() >= 2) {
        return myAttribute.getDisplayValue();
      }
    }
    return null;
  }

  protected void setValue(@Nullable final String value) {
    final XmlTag tag = ensureTagExists();
    final String attributeName = getXmlElementName();
    final String namespace = getXmlElementNamespace();
    final String oldValue = StringUtil.unescapeXml(tag.getAttributeValue(attributeName, namespace));
    final String newValue = XmlStringUtil.escapeString(value);
    if (Comparing.equal(oldValue, newValue)) return;

    getManager().runChange(new Runnable() {
      public void run() {
        try {
          myAttribute = tag.setAttribute(attributeName, namespace, newValue);
        }
        catch (IncorrectOperationException e) {
          LOG.error(e);
        }
      }
    });
    final DomElement proxy = getProxy();
    getManager().fireEvent(oldValue != null ? new ElementChangedEvent(proxy) : new ElementDefinedEvent(proxy));
  }

}
