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
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomElementVisitor;
import com.intellij.util.xml.GenericAttributeValue;
import com.intellij.util.xml.events.ElementChangedEvent;
import com.intellij.util.xml.events.ElementDefinedEvent;
import com.intellij.xml.util.XmlStringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author peter
 */
public class AttributeChildInvocationHandler extends DomInvocationHandler<AttributeChildDescriptionImpl> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.xml.impl.AttributeChildInvocationHandler");

  protected AttributeChildInvocationHandler(final EvaluatedXmlName attributeName,
                                            final AttributeChildDescriptionImpl description,
                                            final DomManagerImpl manager,
                                            final DomParentStrategy strategy) {
    super(description.getType(), strategy, attributeName, description, manager, false);
  }

  public void acceptChildren(DomElementVisitor visitor) {
  }

  protected final XmlTag setEmptyXmlTag() {
    return ensureTagExists();
  }

  protected boolean isAttribute() {
    return true;
  }

  protected XmlElement recomputeXmlElement(@NotNull final DomInvocationHandler parent) {
    if (!parent.isValid()) return null;

    final XmlTag tag = parent.getXmlTag();
    if (tag == null) return null;

    return tag.getAttribute(getXmlElementName(), getXmlName().getNamespace(tag));
  }

  public final XmlAttribute ensureXmlElementExists() {
    XmlAttribute attribute = (XmlAttribute)getXmlElement();
    if (attribute != null) return attribute;

    final DomManagerImpl manager = getManager();
    final boolean b = manager.setChanging(true);
    try {
      attribute = ensureTagExists().setAttribute(getXmlElementName(), getXmlElementNamespace(), "");
      setXmlElement(attribute);
      getManager().cacheHandler(attribute, this);
      manager.fireEvent(new ElementDefinedEvent(getProxy()));
      return attribute;
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

  public final void undefineInternal() {
    final XmlTag tag = getXmlTag();
    if (tag != null) {
      getManager().runChange(new Runnable() {
        public void run() {
          try {
            setXmlElement(null);
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
    final XmlAttribute attribute = (XmlAttribute)getXmlElement();
    if (attribute != null) {
      final XmlAttributeValue value = attribute.getValueElement();
      if (value != null && value.getTextLength() >= 2) {
        return attribute.getDisplayValue();
      }
    }
    return null;
  }

  public void copyFrom(final DomElement other) {
    setValue(((GenericAttributeValue) other).getStringValue());
  }

  protected void setValue(@Nullable final String value) {
    final XmlTag tag = ensureTagExists();
    final String attributeName = getXmlElementName();
    final String namespace = getXmlElementNamespace();
    final String oldValue = StringUtil.unescapeXml(tag.getAttributeValue(attributeName, namespace));
    final String newValue = XmlStringUtil.escapeString(value);
    if (Comparing.equal(oldValue, newValue, true)) return;

    getManager().runChange(new Runnable() {
      public void run() {
        try {
          XmlAttribute attribute = tag.setAttribute(attributeName, namespace, newValue);
          setXmlElement(attribute);
          getManager().cacheHandler(attribute, AttributeChildInvocationHandler.this);
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
