/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml.impl;

import com.intellij.util.xml.ElementPresentation;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiLock;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlElement;
import com.intellij.util.xml.*;
import com.intellij.util.xml.reflect.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.lang.annotation.Annotation;
import java.util.*;

/**
 * @author peter
 */
public class DomFileElementImpl<T extends DomElement> implements DomFileElement<T> {
  private static final DomGenericInfo EMPTY_DOM_GENERIC_INFO = new DomGenericInfo() {
    public Collection<Method> getFixedChildrenGetterMethods() {
      return Collections.emptyList();
    }

    public Collection<Method> getCollectionChildrenGetterMethods() {
      return Collections.emptyList();
    }

    public int getFixedChildIndex(Method method) {
      return 0;
    }

    public String getTagName(Method method) {
      return "NO TAG NAME";
    }

    @Nullable
    public XmlElement getNameElement(DomElement element) {
      return null;
    }

    @Nullable
    public GenericDomValue getNameDomElement(DomElement element) {
      return null;
    }

    @Nullable
    public String getElementName(DomElement element) {
      return null;
    }

    @NotNull
    public List<DomChildrenDescription> getChildrenDescriptions() {
      return Collections.emptyList();
    }

    @NotNull
    public List<DomFixedChildDescription> getFixedChildrenDescriptions() {
      return Collections.emptyList();
    }

    @NotNull
    public List<DomCollectionChildDescription> getCollectionChildrenDescriptions() {
      return Collections.emptyList();
    }

    @NotNull
    public List<DomAttributeChildDescription> getAttributeChildrenDescriptions() {
      return Collections.emptyList();
    }

    @Nullable
    public DomChildrenDescription getChildDescription(String tagName) {
      return null;
    }

    public boolean isTagValueElement() {
      return false;
    }

    @Nullable
    public DomFixedChildDescription getFixedChildDescription(String tagName) {
      return null;
    }

    @Nullable
    public DomCollectionChildDescription getCollectionChildDescription(String tagName) {
      return null;
    }

    public DomAttributeChildDescription getAttributeChildDescription(String attributeName) {
      return null;
    }

    public Type[] getConcreteInterfaceVariants() {
      return new Class[]{DomFileElement.class};
    }
  };

  private final XmlFile myFile;
  private final Class<T> myRootElementClass;
  private final String myRootTagName;
  private final DomManagerImpl myManager;
  private DomRootInvocationHandler myRootHandler;
  private Map<Key,Object> myUserData = new HashMap<Key, Object>();

  protected DomFileElementImpl(final XmlFile file,
                               final Class<T> rootElementClass,
                               final String rootTagName,
                               final DomManagerImpl manager) {
    myFile = file;
    myRootElementClass = rootElementClass;
    myRootTagName = rootTagName;
    myManager = manager;
  }

  public final XmlFile getFile() {
    return myFile;
  }

  @Nullable
  public XmlTag getRootTag() {
    final XmlDocument document = myFile.getDocument();
    if (document != null) {
      final XmlTag tag = document.getRootTag();
      if (tag != null && myRootTagName.equals(tag.getName())) {
        return tag;
      }
    }
    return null;
  }

  public final DomManagerImpl getManager() {
    return myManager;
  }

  public final Type getDomElementType() {
    return getClass();
  }

  public DomNameStrategy getNameStrategy() {
    return getRootHandler().getNameStrategy();
  }

  @NotNull
  public ElementPresentation getPresentation() {
    return new DomElementPresentation() {

      public String getElementName() {
        return "<ROOT>";
      }

      public String getTypeName() {
        return "<ROOT>";
      }

      public Icon getIcon() {
        return null;
      }
    };
  }

  public GlobalSearchScope getResolveScope() {
    return myFile.getResolveScope();
  }

  public <T extends DomElement> T getParentOfType(Class<T> requiredClass, boolean strict) {
    return DomFileElement.class.isAssignableFrom(requiredClass) && !strict ? (T)this : null;
  }

  public Module getModule() {
    return ModuleUtil.findModuleForPsiElement(getFile());
  }

  public void copyFrom(DomElement other) {
    throw new UnsupportedOperationException("Method copyFrom is not yet implemented in " + getClass().getName());
  }

  public final <T extends DomElement> T createMockCopy(final boolean physical) {
    throw new UnsupportedOperationException("Method createMockCopy is not yet implemented in " + getClass().getName());
  }

  public final <T extends DomElement> T createStableCopy() {
    return (T)this;
  }

  public Collection<DomElement> getAllChildren() {
    return (Collection<DomElement>)Collections.singleton(getRootElement());
  }

  @NotNull
  public final T getRootElement() {
    return (T)getRootHandler().getProxy();
  }

  protected final DomRootInvocationHandler getRootHandler() {
    synchronized (PsiLock.LOCK) {
      if (myRootHandler == null/* || !myRootHandler.isValid()*/) {
        final XmlTag tag = getRootTag();
        myRootHandler = new DomRootInvocationHandler(myRootElementClass, tag, this, myRootTagName);
      }
      return myRootHandler;
    }
  }

  protected final void resetRoot() {
    if (myRootHandler != null) {
      myRootHandler.detach(true);
      myRootHandler = null;
    }
  }

  public String toString() {
    return "File " + myFile.toString();
  }

  public <T extends Annotation> T getAnnotation(Class<T> annotationClass) {
    return null;
  }

  public final XmlTag getXmlTag() {
    return null;
  }

  @NotNull
  public DomFileElementImpl getRoot() {
    return this;
  }

  public DomElement getParent() {
    return null;
  }

  public final XmlTag ensureTagExists() {
    return null;
  }

  public final XmlElement getXmlElement() {
    return getFile();
  }

  public final XmlElement ensureXmlElementExists() {
    return ensureTagExists();
  }

  public void undefine() {
  }

  public boolean isValid() {
    return myFile.isValid();
  }

  @NotNull
  public final DomGenericInfo getGenericInfo() {
    return EMPTY_DOM_GENERIC_INFO;
  }

  @NotNull
  public String getXmlElementName() {
    return "";
  }

  public void accept(final DomElementVisitor visitor) {
    myManager.getVisitorDescription(visitor.getClass()).acceptElement(visitor, this);
  }

  public void acceptChildren(DomElementVisitor visitor) {
    getRootHandler().accept(visitor);
  }

  public <T> T getUserData(Key<T> key) {
    return (T)myUserData.get(key);
  }

  public <T> void putUserData(Key<T> key, T value) {
    myUserData.put(key, value);
  }
}
