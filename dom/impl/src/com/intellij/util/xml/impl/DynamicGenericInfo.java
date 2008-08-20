/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.util.xml.impl;

import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.ArrayUtil;
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
  private final Project myProject;
  private final ThreadLocal<Boolean> myComputing = new ThreadLocal<Boolean>();
  private boolean myInitialized;
  private ChildrenDescriptionsHolder<AttributeChildDescriptionImpl> myAttributes;
  private ChildrenDescriptionsHolder<FixedChildDescriptionImpl> myFixeds;
  private ChildrenDescriptionsHolder<CollectionChildDescriptionImpl> myCollections;
  private CustomDomChildrenDescriptionImpl myCustomChildren;

  public DynamicGenericInfo(@NotNull final DomInvocationHandler handler, final StaticGenericInfo staticGenericInfo, final Project project) {
    myInvocationHandler = handler;
    myStaticGenericInfo = staticGenericInfo;
    myProject = project;

    myAttributes = staticGenericInfo.getAttributes();
    myFixeds = staticGenericInfo.getFixed();
    myCollections = staticGenericInfo.getCollections();
  }

  public Invocation createInvocation(final JavaMethod method) {
    return myStaticGenericInfo.createInvocation(method);
  }

  public final void checkInitialized() {
    if (myInitialized) return;
    if (myComputing.get() == Boolean.TRUE) return;

    myComputing.set(Boolean.TRUE);
    try {
      synchronized (myInvocationHandler) {
        if (myInitialized) return;
        myStaticGenericInfo.buildMethodMaps();
        myAttributes = myStaticGenericInfo.getAttributes();
        myFixeds = myStaticGenericInfo.getFixed();
        myCollections = myStaticGenericInfo.getCollections();
      }

      final CustomDomChildrenDescriptionImpl description = myStaticGenericInfo.getCustomNameChildrenDescription();
      final List<XmlTag> customTags = description == null ? null : CustomDomChildrenDescriptionImpl.CUSTOM_TAGS_GETTER.fun(myInvocationHandler);

      DomExtensionsRegistrarImpl registrar = runDomExtenders();

      synchronized (myInvocationHandler) {
        if (myInitialized) return;
        if (registrar != null) {
          final List<DomExtensionImpl> fixeds = registrar.getFixeds();
          final List<DomExtensionImpl> collections = registrar.getCollections();
          if (!fixeds.isEmpty() || !collections.isEmpty()) {
            if (customTags != null) {
              for (final XmlTag tag : customTags) {
                final DomInvocationHandler handler = myInvocationHandler.getManager().getCachedHandler(tag);
                if (handler != null) {
                  handler.detach();
                }
              }
            }
          }


          final List<DomExtensionImpl> attributes = registrar.getAttributes();
          if (!attributes.isEmpty()) {
            myAttributes = new ChildrenDescriptionsHolder<AttributeChildDescriptionImpl>(myStaticGenericInfo.getAttributes());
            for (final DomExtensionImpl extension : attributes) {
              myAttributes.addDescription(extension.addAnnotations(new AttributeChildDescriptionImpl(extension.getXmlName(), extension.getType())));
            }
          }
          if (!fixeds.isEmpty()) {
            myFixeds = new ChildrenDescriptionsHolder<FixedChildDescriptionImpl>(myStaticGenericInfo.getFixed());
            for (final DomExtensionImpl extension : fixeds) {
              myFixeds.addDescription(extension.addAnnotations(new FixedChildDescriptionImpl(extension.getXmlName(), extension.getType(), extension.getCount(), ArrayUtil.EMPTY_COLLECTION_ARRAY)));
            }
          }
          if (!collections.isEmpty()) {
            myCollections = new ChildrenDescriptionsHolder<CollectionChildDescriptionImpl>(myStaticGenericInfo.getCollections());
            for (final DomExtensionImpl extension : collections) {
              myCollections.addDescription(extension.addAnnotations(new CollectionChildDescriptionImpl(extension.getXmlName(), extension.getType(), Collections.EMPTY_LIST, Collections.EMPTY_LIST, Collections.EMPTY_LIST, Collections.EMPTY_LIST, Collections.EMPTY_LIST, Collections.EMPTY_LIST)));
            }
          }

          final DomExtensionImpl extension = registrar.getCustomChildrenType();
          if (extension != null) {
            myCustomChildren = new CustomDomChildrenDescriptionImpl(null, extension.getType());
          }
        }
        myInitialized = true;
      }
    }
    finally {
      myComputing.set(null);
    }
  }

  @Nullable
  private DomExtensionsRegistrarImpl runDomExtenders() {
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
    return registrar;
  }

  public XmlElement getNameElement(DomElement element) {
    return myStaticGenericInfo.getNameElement(element);
  }

  public GenericDomValue getNameDomElement(DomElement element) {
    return myStaticGenericInfo.getNameDomElement(element);
  }

  @Nullable
  public CustomDomChildrenDescriptionImpl getCustomNameChildrenDescription() {
    if (myCustomChildren != null) return myCustomChildren;
    return myStaticGenericInfo.getCustomNameChildrenDescription();
  }

  public String getElementName(DomElement element) {
    return myStaticGenericInfo.getElementName(element);
  }

  @NotNull
  public List<AbstractDomChildDescriptionImpl> getChildrenDescriptions() {
    checkInitialized();
    final ArrayList<AbstractDomChildDescriptionImpl> list = new ArrayList<AbstractDomChildDescriptionImpl>();
    list.addAll(myAttributes.getDescriptions());
    list.addAll(myFixeds.getDescriptions());
    list.addAll(myCollections.getDescriptions());
    ContainerUtil.addIfNotNull(myStaticGenericInfo.getCustomNameChildrenDescription(), list);
    return list;
  }

  @NotNull
  public final List<FixedChildDescriptionImpl> getFixedChildrenDescriptions() {
    checkInitialized();
    return myFixeds.getDescriptions();
  }

  @NotNull
  public final List<CollectionChildDescriptionImpl> getCollectionChildrenDescriptions() {
    checkInitialized();
    return myCollections.getDescriptions();
  }

  public FixedChildDescriptionImpl getFixedChildDescription(String tagName) {
    checkInitialized();
    return myFixeds.findDescription(tagName);
  }

  public DomFixedChildDescription getFixedChildDescription(@NonNls String tagName, @NonNls String namespace) {
    checkInitialized();
    return myFixeds.getDescription(tagName, namespace);
  }

  public CollectionChildDescriptionImpl getCollectionChildDescription(String tagName) {
    checkInitialized();
    return myCollections.findDescription(tagName);
  }

  public DomCollectionChildDescription getCollectionChildDescription(@NonNls String tagName, @NonNls String namespace) {
    checkInitialized();
    return myCollections.getDescription(tagName, namespace);
  }

  public AttributeChildDescriptionImpl getAttributeChildDescription(String attributeName) {
    checkInitialized();
    return myAttributes.findDescription(attributeName);
  }


  public DomAttributeChildDescription getAttributeChildDescription(@NonNls String attributeName, @NonNls String namespace) {
    checkInitialized();
    return myAttributes.getDescription(attributeName, namespace);
  }

  public Type[] getConcreteInterfaceVariants() {
    return myStaticGenericInfo.getConcreteInterfaceVariants();
  }

  public boolean isTagValueElement() {
    return myStaticGenericInfo.isTagValueElement();
  }

  @NotNull
  public List<AttributeChildDescriptionImpl> getAttributeChildrenDescriptions() {
    checkInitialized();
    return myAttributes.getDescriptions();
  }

}