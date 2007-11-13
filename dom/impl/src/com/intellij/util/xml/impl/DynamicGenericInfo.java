/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.util.xml.impl;

import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.psi.PsiManager;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.xml.XmlElement;
import com.intellij.util.ArrayUtil;
import com.intellij.util.concurrency.JBLock;
import com.intellij.util.concurrency.JBReentrantReadWriteLock;
import com.intellij.util.concurrency.LockFactory;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.GenericDomValue;
import com.intellij.util.xml.JavaMethod;
import com.intellij.util.xml.reflect.*;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author peter
 */
public class DynamicGenericInfo extends DomGenericInfoEx {
  private final StaticGenericInfo myStaticGenericInfo;
  @NotNull private final DomInvocationHandler myInvocationHandler;
  private final CachedValue<Object> myCachedValue;
  private final Project myProject;
  private final ThreadLocal<Boolean> myComputing = new ThreadLocal<Boolean>();
  private ChildrenDescriptionsHolder<AttributeChildDescriptionImpl> myAttributes;
  private ChildrenDescriptionsHolder<FixedChildDescriptionImpl> myFixeds;
  private ChildrenDescriptionsHolder<CollectionChildDescriptionImpl> myCollections;
  private static final JBReentrantReadWriteLock rwl = LockFactory.createReadWriteLock();
  private static final JBLock r = rwl.readLock();
  private static final JBLock w = rwl.writeLock();

  public DynamicGenericInfo(@NotNull final DomInvocationHandler handler, final StaticGenericInfo staticGenericInfo, final Project project) {
    myInvocationHandler = handler;
    myStaticGenericInfo = staticGenericInfo;
    myProject = project;
    myCachedValue = PsiManager.getInstance(myProject).getCachedValuesManager().createCachedValue(new MyCachedValueProvider(), false);

    myAttributes = staticGenericInfo.getAttributes();
    myFixeds = staticGenericInfo.getFixed();
    myCollections = staticGenericInfo.getCollections();
  }

  public final void checkInitialized() {
    r.lock();
    try {
      _checkInitialized();
    } finally {
      r.unlock();
    }
  }

  public Invocation createInvocation(final JavaMethod method) {
    return myStaticGenericInfo.createInvocation(method);
  }

  private void _checkInitialized() {
    myStaticGenericInfo.buildMethodMaps();
    if (myCachedValue.hasUpToDateValue()) return;
    if (myComputing.get() != null) return;
    r.unlock();
    boolean rlocked = false;
    try {
      w.lock();
      try {
        if (myCachedValue.hasUpToDateValue()) return;

        myAttributes = myStaticGenericInfo.getAttributes();
        myFixeds = myStaticGenericInfo.getFixed();
        myCollections = myStaticGenericInfo.getCollections();

        myComputing.set(Boolean.TRUE);
      } finally {
        w.unlock();
      }

      DomExtensionsRegistrarImpl registrar = null;
      final DomElement domElement = myInvocationHandler.getProxy();
      for (final DomExtenderEP extenderEP : Extensions.getExtensions(DomExtenderEP.EP_NAME)) {
        registrar = extenderEP.extend(myProject, domElement, registrar);
      }

      final AbstractDomChildDescriptionImpl description = myInvocationHandler.getChildDescription();
      if (description != null) {
        final List<DomExtender> extenders = description.getUserData(DomExtensionImpl.DOM_EXTENDER_KEY);
        if (extenders != null) {
          if (registrar == null) registrar = new DomExtensionsRegistrarImpl();
          for (final DomExtender extender : extenders) {
            extender.registerExtensions(domElement, registrar);
          }
        }
      }

      w.lock();
      try {
        if (myCachedValue.hasUpToDateValue()) return;
        if (registrar != null) {
          final List<DomExtensionImpl> attributes = registrar.getAttributes();
          if (!attributes.isEmpty()) {
            myAttributes = new ChildrenDescriptionsHolder<AttributeChildDescriptionImpl>(myStaticGenericInfo.getAttributes());
            for (final DomExtensionImpl extension : attributes) {
              myAttributes.addDescription(extension.addAnnotations(new AttributeChildDescriptionImpl(extension.getXmlName(), extension.getType())));
            }
          }
          final List<DomExtensionImpl> fixeds = registrar.getFixeds();
          if (!fixeds.isEmpty()) {
            myFixeds = new ChildrenDescriptionsHolder<FixedChildDescriptionImpl>(myStaticGenericInfo.getFixed());
            for (final DomExtensionImpl extension : fixeds) {
              myFixeds.addDescription(extension.addAnnotations(new FixedChildDescriptionImpl(extension.getXmlName(), extension.getType(), extension.getCount(), ArrayUtil.EMPTY_COLLECTION_ARRAY)));
            }
          }
          final List<DomExtensionImpl> collections = registrar.getCollections();
          if (!collections.isEmpty()) {
            myCollections = new ChildrenDescriptionsHolder<CollectionChildDescriptionImpl>(myStaticGenericInfo.getCollections());
            for (final DomExtensionImpl extension : collections) {
              myCollections.addDescription(extension.addAnnotations(new CollectionChildDescriptionImpl(extension.getXmlName(), extension.getType(), Collections.EMPTY_LIST, Collections.EMPTY_LIST, Collections.EMPTY_LIST, Collections.EMPTY_LIST, Collections.EMPTY_LIST, Collections.EMPTY_LIST)));
            }
          }
        }
        ((MyCachedValueProvider)myCachedValue.getValueProvider()).deps = registrar == null ? ArrayUtil.EMPTY_OBJECT_ARRAY : registrar.getDependencies();
        myCachedValue.getValue();
      }
      finally {
        myComputing.set(null);
        r.lock();
        rlocked = true;
        w.unlock();
      }
    }
    finally {
      if (!rlocked) {
        r.lock();
      }
    }

  }

  public XmlElement getNameElement(DomElement element) {
    return myStaticGenericInfo.getNameElement(element);
  }

  public GenericDomValue getNameDomElement(DomElement element) {
    return myStaticGenericInfo.getNameDomElement(element);
  }

  @Nullable
  public CustomDomChildrenDescriptionImpl getCustomNameChildrenDescription() {
    return myStaticGenericInfo.getCustomNameChildrenDescription();
  }

  public String getElementName(DomElement element) {
    return myStaticGenericInfo.getElementName(element);
  }

  @NotNull
  public List<AbstractDomChildDescriptionImpl> getChildrenDescriptions() {
    r.lock();
    try {
      _checkInitialized();
      final ArrayList<AbstractDomChildDescriptionImpl> list = new ArrayList<AbstractDomChildDescriptionImpl>();
      list.addAll(myAttributes.getDescriptions());
      list.addAll(myFixeds.getDescriptions());
      list.addAll(myCollections.getDescriptions());
      ContainerUtil.addIfNotNull(myStaticGenericInfo.getCustomNameChildrenDescription(), list);
      return list;
    }
    finally {
      r.unlock();
    }
  }

  @NotNull
  public final List<FixedChildDescriptionImpl> getFixedChildrenDescriptions() {
    r.lock();
    try {
      _checkInitialized();
      return myFixeds.getDescriptions();
    }
    finally {
      r.unlock();
    }
  }

  @NotNull
  public final List<CollectionChildDescriptionImpl> getCollectionChildrenDescriptions() {
    r.lock();
    try {
      _checkInitialized();
      return myCollections.getDescriptions();
    }
    finally {
      r.unlock();
    }
  }

  public FixedChildDescriptionImpl getFixedChildDescription(String tagName) {
    r.lock();
    try {
      _checkInitialized();
      return myFixeds.findDescription(tagName);
    }
    finally {
      r.unlock();
    }
  }

  public DomFixedChildDescription getFixedChildDescription(@NonNls String tagName, @NonNls String namespace) {
    r.lock();
    try {
      _checkInitialized();
      return myFixeds.getDescription(tagName, namespace);
    }
    finally {
      r.unlock();
    }
  }

  public CollectionChildDescriptionImpl getCollectionChildDescription(String tagName) {
    r.lock();
    try {
      _checkInitialized();
      return myCollections.findDescription(tagName);
    }
    finally {
      r.unlock();
    }
  }

  public DomCollectionChildDescription getCollectionChildDescription(@NonNls String tagName, @NonNls String namespace) {
    r.lock();
    try {
      _checkInitialized();
      return myCollections.getDescription(tagName, namespace);
    }
    finally {
      r.unlock();
    }
  }

  public AttributeChildDescriptionImpl getAttributeChildDescription(String attributeName) {
    r.lock();
    try {
      _checkInitialized();
      return myAttributes.findDescription(attributeName);
    }
    finally {
      r.unlock();
    }
  }


  public DomAttributeChildDescription getAttributeChildDescription(@NonNls String attributeName, @NonNls String namespace) {
    r.lock();
    try {
      _checkInitialized();
      return myAttributes.getDescription(attributeName, namespace);
    }
    finally {
      r.unlock();
    }
  }

  public Type[] getConcreteInterfaceVariants() {
    return myStaticGenericInfo.getConcreteInterfaceVariants();
  }

  public boolean isTagValueElement() {
    return myStaticGenericInfo.isTagValueElement();
  }

  @NotNull
  public List<AttributeChildDescriptionImpl> getAttributeChildrenDescriptions() {
    r.lock();
    try {
      _checkInitialized();
      return myAttributes.getDescriptions();
    }
    finally {
      r.unlock();
    }
  }

  private static class MyCachedValueProvider implements CachedValueProvider<Object> {
    Object[] deps = new Object[]{ModificationTracker.EVER_CHANGED};

    public Result<Object> compute() {
      return new Result<Object>(Boolean.TRUE, deps);
    }
  }
}