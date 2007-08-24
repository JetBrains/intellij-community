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
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.GenericDomValue;
import com.intellij.util.xml.XmlName;
import com.intellij.util.xml.reflect.*;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * @author peter
 */
public class DynamicGenericInfo implements DomGenericInfo {
  private final StaticGenericInfo myStaticGenericInfo;
  private final DomInvocationHandler myInvocationHandler;
  private final CachedValue<Object> myCachedValue;
  private final Project myProject;
  private boolean myComputing;
  private final ChildrenDescriptionsHolder<AttributeChildDescriptionImpl> myAttributes;
  private final ChildrenDescriptionsHolder<FixedChildDescriptionImpl> myFixeds;
  private final ChildrenDescriptionsHolder<CollectionChildDescriptionImpl> myCollections;
  private static final ReentrantReadWriteLock rwl = new ReentrantReadWriteLock();
  private static final ReentrantReadWriteLock.ReadLock r = rwl.readLock();
  private static final ReentrantReadWriteLock.WriteLock w = rwl.writeLock();

  public DynamicGenericInfo(final DomInvocationHandler handler, final StaticGenericInfo staticGenericInfo, final Project project) {
    myInvocationHandler = handler;
    myStaticGenericInfo = staticGenericInfo;
    myProject = project;
    myCachedValue = PsiManager.getInstance(myProject).getCachedValuesManager().createCachedValue(new MyCachedValueProvider(), false);

    myAttributes = new ChildrenDescriptionsHolder<AttributeChildDescriptionImpl>(staticGenericInfo.getAttributes());
    myFixeds = new ChildrenDescriptionsHolder<FixedChildDescriptionImpl>(staticGenericInfo.getFixed());
    myCollections = new ChildrenDescriptionsHolder<CollectionChildDescriptionImpl>(staticGenericInfo.getCollections());
  }

  public final void checkInitialized() {
    r.lock();
    try {
      _checkInitialized();
    } finally {
      r.unlock();
    }
  }

  private void _checkInitialized() {
    myStaticGenericInfo.buildMethodMaps();
    if (myCachedValue.hasUpToDateValue()) return;
    if (myComputing) return;
    if (myInvocationHandler != null) {
      r.unlock();
      boolean rlocked = false;
      try {
        w.lock();
        try {
          if (myCachedValue.hasUpToDateValue()) return;

          myAttributes.clear();
          myFixeds.clear();
          myCollections.clear();

          myComputing = true;
        } finally {
          w.unlock();
        }

        DomExtensionsRegistrarImpl registrar = null;
        final DomElement domElement = myInvocationHandler.getProxy();
        for (final DomExtenderEP extenderEP : Extensions.getExtensions(DomExtenderEP.EP_NAME)) {
          registrar = extenderEP.extend(myProject, domElement, registrar);
        }

        w.lock();
        try {
          if (myCachedValue.hasUpToDateValue()) return;
          if (registrar != null) {
            for (final DomExtensionImpl extension : registrar.getAttributes()) {
              myAttributes.addDescription(extension.addAnnotations(new AttributeChildDescriptionImpl(extension.getXmlName(), extension.getType())));
            }
            for (final DomExtensionImpl extension : registrar.getFixeds()) {
              myFixeds.addDescription(extension.addAnnotations(new FixedChildDescriptionImpl(extension.getXmlName(), extension.getType(), extension.getCount(), ArrayUtil.EMPTY_COLLECTION_ARRAY)));
            }
            for (final DomExtensionImpl extension : registrar.getCollections()) {
              myCollections.addDescription(extension.addAnnotations(new CollectionChildDescriptionImpl(extension.getXmlName(), extension.getType(), Collections.EMPTY_LIST, Collections.EMPTY_LIST, Collections.EMPTY_LIST, Collections.EMPTY_LIST, Collections.EMPTY_LIST, Collections.EMPTY_LIST)));
            }
          }
          ((MyCachedValueProvider)myCachedValue.getValueProvider()).deps = registrar == null ? ArrayUtil.EMPTY_OBJECT_ARRAY : registrar.getDependencies();
          myCachedValue.getValue();
        }
        finally {
          myComputing = false;
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
  }

  public XmlElement getNameElement(DomElement element) {
    return myStaticGenericInfo.getNameElement(element);
  }

  public GenericDomValue getNameDomElement(DomElement element) {
    return myStaticGenericInfo.getNameDomElement(element);
  }

  @Nullable
  public CustomDomChildrenDescriptionImpl getCustomNameChildrenDescription() {
    r.lock();
    try {
      checkInitialized();
      return myStaticGenericInfo.getCustomDescription();
    } finally{
      r.unlock();
    }
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
      ContainerUtil.addIfNotNull(myStaticGenericInfo.getCustomDescription(), list);
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

  @Nullable
  public FixedChildDescriptionImpl getFixedChildDescription(XmlName tagName) {
    r.lock();
    try {
      _checkInitialized();
      return myFixeds.getDescription(tagName);
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

  @Nullable
  public CollectionChildDescriptionImpl getCollectionChildDescription(XmlName tagName) {
    r.lock();
    try {
      _checkInitialized();
      return myCollections.getDescription(tagName);
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

  @Nullable
  public AttributeChildDescriptionImpl getAttributeChildDescription(XmlName attributeName) {
    r.lock();
    try {
      _checkInitialized();
      return myAttributes.getDescription(attributeName);
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

  @Nullable
  public final AbstractDomChildDescriptionImpl findChildrenDescription(DomInvocationHandler handler, final String localName, String namespace,
                                                               boolean attribute,
                                                               final String qName) {
    for (final AbstractDomChildDescriptionImpl description : getChildrenDescriptions()) {
      if (description instanceof DomChildDescriptionImpl && description instanceof AttributeChildDescriptionImpl == attribute) {
        final EvaluatedXmlName xmlName = handler.createEvaluatedXmlName(((DomChildDescriptionImpl)description).getXmlName());
        if (DomImplUtil.isNameSuitable(xmlName, localName, qName, namespace, handler)) {
          return description;
        }
      }
    }
    return attribute ? null : getCustomNameChildrenDescription();
  }

  private static class MyCachedValueProvider implements CachedValueProvider<Object> {
    Object[] deps = new Object[]{ModificationTracker.EVER_CHANGED};

    public Result<Object> compute() {
      return new Result<Object>(Boolean.TRUE, deps);
    }
  }
}