/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml.impl;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.util.Factory;
import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.xml.*;
import com.intellij.util.xml.reflect.*;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.lang.ref.WeakReference;
import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

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

    public @NonNls
    String getTagName(Method method) {
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
    public CustomDomChildrenDescription getCustomNameChildrenDescription() {
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

    public boolean isTagValueElement() {
      return false;
    }

    @Nullable
    public DomFixedChildDescription getFixedChildDescription(String tagName) {
      return null;
    }

    @Nullable
    public DomFixedChildDescription getFixedChildDescription(@NonNls String tagName, @NonNls String namespace) {
      return null;
    }

    @Nullable
    public DomCollectionChildDescription getCollectionChildDescription(String tagName) {
      return null;
    }

    @Nullable
    public DomCollectionChildDescription getCollectionChildDescription(@NonNls String tagName, @NonNls String namespace) {
      return null;
    }

    public DomAttributeChildDescription getAttributeChildDescription(String attributeName) {
      return null;
    }

    @Nullable
    public DomAttributeChildDescription getAttributeChildDescription(@NonNls String attributeName, @NonNls String namespace) {
      return null;
    }

    public Type[] getConcreteInterfaceVariants() {
      return new Class[]{DomFileElement.class};
    }
  };

  private final XmlFile myFile;
  private final Class<T> myRootElementClass;
  private final EvaluatedXmlNameImpl myRootTagName;
  private final DomManagerImpl myManager;
  private WeakReference<DomRootInvocationHandler> myRootHandler;
  private Map<Key,Object> myUserData = new HashMap<Key, Object>();
  private long myModificationCount;
  private boolean myInvalidated;

  private static final ReentrantReadWriteLock rwl = new ReentrantReadWriteLock();
  private static final Lock r = rwl.readLock();
  private static final Lock w = rwl.writeLock();

  protected DomFileElementImpl(final XmlFile file,
                               final Class<T> rootElementClass,
                               final EvaluatedXmlNameImpl rootTagName,
                               final DomManagerImpl manager) {
    myFile = file;
    myRootElementClass = rootElementClass;
    myRootTagName = rootTagName;
    myManager = manager;
  }

  @NotNull
  public final XmlFile getFile() {
    return myFile;
  }

  @NotNull
  public XmlFile getOriginalFile() {
    final PsiFile originalFile = myFile.getOriginalFile();
    if (originalFile != null) {
      return (XmlFile)originalFile;
    }
    return myFile;
  }

  @Nullable
  public XmlTag getRootTag() {
    final XmlDocument document = myFile.getDocument();
    if (document != null) {
      final XmlTag tag = document.getRootTag();
      if (tag != null && myRootTagName.getXmlName().getLocalName().equals(tag.getLocalName()) && myRootTagName.isNamespaceAllowed(this, tag.getNamespace())) {
        return tag;
      }
    }
    return null;
  }

  @NotNull
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

      public @NonNls String getElementName() {
        return "<ROOT>";
      }

      public @NonNls String getTypeName() {
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

  @Nullable
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
    return myManager.createStableValue(new Factory<T>() {
      @Nullable
      public T create() {
        return (T)myManager.getFileElement(myFile);
      }
    });
  }

  @NotNull
  public String getXmlElementNamespace() {
    return "";
  }

  @Nullable
  @NonNls
  public String getXmlElementNamespaceKey() {
    return null;
  }

  @NotNull
  public final T getRootElement() {
    return (T)getRootHandler().getProxy();
  }

  public Class<T> getRootElementClass() {
    return myRootElementClass;
  }

  @NotNull
  public DomFileDescription<T> getFileDescription() {
    final DomFileDescription description = myManager.getDomFileDescription(getFile());
    assert description != null;
    return description;
  }

  protected final DomRootInvocationHandler getRootHandler() {
    r.lock();
    if (myRootHandler == null || myRootHandler.get() == null) {
      r.unlock();
      final XmlTag tag = getRootTag(); // do not take root tag under our lock to prevent dead lock with PsiLock
      w.lock();
      try {
        if (myRootHandler == null || myRootHandler.get() == null) {
          myRootHandler = new WeakReference<DomRootInvocationHandler>(new DomRootInvocationHandler(myRootElementClass, tag, this, myRootTagName));
        }
      }
      finally{
        w.unlock();
        r.lock();
      }
    }
    final DomRootInvocationHandler rootHandler = myRootHandler.get();
    r.unlock();
    return rootHandler;
  }

  protected final void resetRoot(boolean invalidate) {
    myInvalidated = invalidate;
    if (myRootHandler != null && myRootHandler.get() != null) {
      myRootHandler.get().detach(true);
      myRootHandler = null;
    }
  }

  public @NonNls String toString() {
    return "File " + myFile.toString();
  }

  public <T extends Annotation> T getAnnotation(Class<T> annotationClass) {
    return null;
  }

  public final XmlTag getXmlTag() {
    return null;
  }

  @NotNull
  public <T extends DomElement> DomFileElementImpl<T> getRoot() {
    return (DomFileElementImpl<T>)this;
  }

  @Nullable
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

  public final boolean isValid() {
    if (myInvalidated) return false;
    myInvalidated = !myFile.isValid() || myManager.getFileElement(myFile) != this;
    return !myInvalidated;
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
    getRootElement().accept(visitor);
  }

  public <T> T getUserData(Key<T> key) {
    return (T)myUserData.get(key);
  }

  public <T> void putUserData(Key<T> key, T value) {
    myUserData.put(key, value);
  }

  public final long getModificationCount() {
    return myModificationCount;
  }

  public final void onModified() {
    myModificationCount++;
  }
}
