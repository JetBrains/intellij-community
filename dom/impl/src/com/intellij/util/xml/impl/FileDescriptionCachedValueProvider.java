/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.util.xml.impl;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.UserDataCache;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomFileDescription;
import com.intellij.util.xml.XmlFileHeader;
import com.intellij.util.xml.XmlName;
import com.intellij.util.xml.events.DomEvent;
import com.intellij.util.xml.events.ElementDefinedEvent;
import com.intellij.util.xml.events.ElementUndefinedEvent;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * @author peter
 */
class FileDescriptionCachedValueProvider<T extends DomElement> {
  private static final Key<CachedValue<XmlFileHeader>> ROOT_TAG_NS_KEY = Key.create("rootTag&ns");
  private static final UserDataCache<CachedValue<XmlFileHeader>,XmlFile,Object> ourRootTagCache = new UserDataCache<CachedValue<XmlFileHeader>, XmlFile, Object>() {
    protected CachedValue<XmlFileHeader> compute(final XmlFile file, final Object o) {
      return file.getManager().getCachedValuesManager().createCachedValue(new CachedValueProvider<XmlFileHeader>() {
        public Result<XmlFileHeader> compute() {
          return new Result<XmlFileHeader>(DomImplUtil.getXmlFileHeader(file), file);
        }
      }, false);
    }
  };

  private final XmlFile myXmlFile;
  private volatile boolean myComputed;
  private volatile DomFileElementImpl<T> myLastResult;
  private final MyCondition myCondition = new MyCondition();

  private final DomManagerImpl myDomManager;

  public FileDescriptionCachedValueProvider(final DomManagerImpl domManager, final XmlFile xmlFile) {
    myDomManager = domManager;
    myXmlFile = xmlFile;
  }

  @Nullable
  public final DomFileElementImpl<T> getFileElement() {
    if (myComputed) return myLastResult;

    final XmlFileHeader rootTagName = getRootTag();
    try {
      _computeFileElement(false, rootTagName);
    }
    finally {
      myXmlFile.putUserData(DomManagerImpl.CACHED_FILE_ELEMENT, myLastResult);
      myComputed = true;
    }
    return myLastResult;
  }

  private List<DomEvent> _computeFileElement(final boolean fireEvents, final XmlFileHeader rootTagName) {
    final DomFileElementImpl<T> lastResult = myLastResult;
    if (!myXmlFile.isValid()) {
      myLastResult = null;
      if (fireEvents && lastResult != null) {
        return Arrays.<DomEvent>asList(new ElementUndefinedEvent(lastResult));
      }
      return Collections.emptyList();
    }

    final DomFileDescription<T> description = findFileDescription(rootTagName);

    final DomFileElementImpl oldValue = getLastValue();
    final List<DomEvent> events = fireEvents ? new SmartList<DomEvent>() : Collections.<DomEvent>emptyList();
    if (oldValue != null) {
      if (fireEvents) {
        events.add(new ElementUndefinedEvent(oldValue));
      }
    }

    if (description == null) {
      myLastResult = null;
      return events;
    }

    final Class<T> rootElementClass = description.getRootElementClass();
    final XmlName xmlName = DomImplUtil.createXmlName(description.getRootTagName(), rootElementClass, null);
    assert xmlName != null;
    final EvaluatedXmlNameImpl rootTagName1 = EvaluatedXmlNameImpl.createEvaluatedXmlName(xmlName, xmlName.getNamespaceKey(), false);
    myLastResult = new DomFileElementImpl<T>(myXmlFile, rootElementClass, rootTagName1, myDomManager, description);

    if (fireEvents) {
      events.add(new ElementDefinedEvent(myLastResult));
    }
    return events;
  }

  @Nullable
  private DomFileDescription<T> findFileDescription(final XmlFileHeader rootTagName) {
    final DomFileDescription<T> mockDescription = myXmlFile.getUserData(DomManagerImpl.MOCK_DESCIPRTION);
    if (mockDescription != null) return mockDescription;

    final XmlFile originalFile = (XmlFile)myXmlFile.getOriginalFile();
    if (originalFile != null) {
      final FileDescriptionCachedValueProvider<T> provider = myDomManager.<T>getOrCreateCachedValueProvider(originalFile);
      final DomFileElementImpl<T> element = provider.getFileElement();
      return element == null ? null : element.getFileDescription();
    }

    //noinspection unchecked
    DomFileDescription<T> description = ContainerUtil.find(myDomManager.getFileDescriptions(rootTagName.getRootTagLocalName()), myCondition);
    if (description == null) {
      description = ContainerUtil.find(myDomManager.getAcceptingOtherRootTagNameDescriptions(), myCondition);
    }
    return description;
  }

  @Nullable
  private XmlFileHeader getRootTag() {
    return myXmlFile.isValid() ? ourRootTagCache.get(ROOT_TAG_NS_KEY, myXmlFile, null).getValue() : XmlFileHeader.EMPTY;
  }

  @Nullable
  final DomFileElementImpl<T> getLastValue() {
    return myLastResult;
  }

  private class MyCondition implements Condition<DomFileDescription> {
    public Module module;

    public boolean value(final DomFileDescription description) {
      return description.isMyFile(myXmlFile, module);
    }
  }

}
