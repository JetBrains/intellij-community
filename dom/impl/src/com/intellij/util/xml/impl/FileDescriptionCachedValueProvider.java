/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.util.xml.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
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
import com.intellij.util.xml.XmlName;
import com.intellij.util.xml.events.DomEvent;
import com.intellij.util.xml.events.ElementDefinedEvent;
import com.intellij.util.xml.events.ElementUndefinedEvent;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * @author peter
 */
class FileDescriptionCachedValueProvider<T extends DomElement> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.xml.impl.FileDescriptionCachedValueProvider");
  private static final Key<CachedValue<String>> ROOT_TAG_NS_KEY = Key.create("rootTag&ns");
  private static final UserDataCache<CachedValue<String>,XmlFile,Object> ourRootTagCache = new UserDataCache<CachedValue<String>, XmlFile, Object>() {
    protected CachedValue<String> compute(final XmlFile file, final Object o) {
      return file.getManager().getCachedValuesManager().createCachedValue(new CachedValueProvider<String>() {
        public Result<String> compute() {
          String rootTagAndNs = null;
          try {
            rootTagAndNs = DomImplUtil.getRootTagName(file);
          }
          catch (IOException e) {
            LOG.info(e);
          }
          return new Result<String>(rootTagAndNs, file);
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

    final String rootTagName = getRootTag();
    try {
      _computeFileElement(false, false, rootTagName);
    }
    finally {
      myXmlFile.putUserData(DomManagerImpl.CACHED_FILE_ELEMENT, myLastResult);
      myComputed = true;
    }
    return myLastResult;
  }

  private List<DomEvent> _computeFileElement(final boolean fireEvents, boolean fireChanged, final String rootTagName) {
    final DomFileElementImpl<T> lastResult = myLastResult;
    if (!myXmlFile.isValid()) {
      myLastResult = null;
      if (fireEvents && lastResult != null) {
        return Arrays.<DomEvent>asList(new ElementUndefinedEvent(lastResult));
      }
      return Collections.emptyList();
    }

    final Module module = ModuleUtil.findModuleForPsiElement(myXmlFile);
    final DomFileDescription<T> description = findFileDescription(module, rootTagName);

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
    final EvaluatedXmlNameImpl rootTagName1 = EvaluatedXmlNameImpl.createEvaluatedXmlName(xmlName, xmlName.getNamespaceKey());
    myLastResult = new DomFileElementImpl<T>(myXmlFile, rootElementClass, rootTagName1, myDomManager, description);

    if (fireEvents) {
      events.add(new ElementDefinedEvent(myLastResult));
    }
    return events;
  }

  @Nullable
  private DomFileDescription<T> findFileDescription(Module module, final String rootTagName) {
    final DomFileDescription<T> mockDescription = myXmlFile.getUserData(DomManagerImpl.MOCK_DESCIPRTION);
    if (mockDescription != null) return mockDescription;

    final XmlFile originalFile = (XmlFile)myXmlFile.getOriginalFile();
    if (originalFile != null) {
      final FileDescriptionCachedValueProvider<T> provider = myDomManager.<T>getOrCreateCachedValueProvider(originalFile);
      final DomFileElementImpl<T> element = provider.getFileElement();
      return element == null ? null : element.getFileDescription();
    }

    //noinspection unchecked
    DomFileDescription<T> description = ContainerUtil.find(myDomManager.getFileDescriptions(rootTagName), myCondition);
    if (description == null) {
      description = ContainerUtil.find(myDomManager.getAcceptingOtherRootTagNameDescriptions(), myCondition);
    }
    return description;
  }

  @Nullable
  private String getRootTag() {
    return myXmlFile.isValid() ? ourRootTagCache.get(ROOT_TAG_NS_KEY, myXmlFile, null).getValue() : null;
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
