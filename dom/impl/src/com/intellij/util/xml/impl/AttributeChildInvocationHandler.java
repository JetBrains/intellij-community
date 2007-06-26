/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Factory;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomElementVisitor;
import com.intellij.util.xml.events.ElementChangedEvent;
import com.intellij.util.xml.events.ElementDefinedEvent;
import com.intellij.util.xml.reflect.DomAttributeChildDescription;
import com.intellij.xml.util.XmlStringUtil;
import com.intellij.xml.util.XmlUtil;
import org.jetbrains.annotations.NotNull;
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

  public boolean isValid() {
    return getParentHandler().isValid();
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
    final DomElement parentCopy = getParent().createStableCopy();
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

  @Nullable
  protected String getValue() {
    final XmlTag tag = getXmlTag();
    if (tag != null) {
      final String s = tag.getAttributeValue(getXmlElementName(), tag.getNamespace());
      if (s != null) {
        return XmlUtil.unescape(s);
      }
    }
    return null;
  }

  protected void setValue(@NotNull final String value) {
    final XmlTag tag = ensureTagExists();
    final String attributeName = getXmlElementName();
    final String namespace = getXmlElementNamespace();
    final String oldValue = XmlUtil.unescape(tag.getAttributeValue(attributeName, namespace));
    final String newValue = XmlStringUtil.escapeString(value);
    if (Comparing.equal(oldValue, newValue)) return;

    getManager().runChange(new Runnable() {
      public void run() {
        try {
          tag.setAttribute(attributeName, namespace, newValue);
        }
        catch (IncorrectOperationException e) {
          LOG.error(e);
        }
      }
    });
    setDefined(true);
    final DomElement proxy = getProxy();
    getManager().fireEvent(oldValue != null ? new ElementChangedEvent(proxy) : new ElementDefinedEvent(proxy));
  }

}
