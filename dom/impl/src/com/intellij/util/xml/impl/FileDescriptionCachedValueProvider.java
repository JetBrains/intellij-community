/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.util.xml.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.util.*;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.ArrayUtil;
import com.intellij.util.SmartList;
import com.intellij.util.concurrency.JBLock;
import com.intellij.util.concurrency.JBReentrantReadWriteLock;
import com.intellij.util.concurrency.LockFactory;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashSet;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomFileDescription;
import com.intellij.util.xml.XmlName;
import com.intellij.util.xml.events.DomEvent;
import com.intellij.util.xml.events.ElementChangedEvent;
import com.intellij.util.xml.events.ElementDefinedEvent;
import com.intellij.util.xml.events.ElementUndefinedEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.*;

/**
 * @author peter
 */
class FileDescriptionCachedValueProvider<T extends DomElement> implements ModificationTracker {
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
  private final ThreadLocal<Boolean> myComputing = new ThreadLocal<Boolean>();
  private DomFileElementImpl<T> myLastResult;
  private final CachedValue<Boolean> myCachedValue;
  private final MyCondition myCondition = new MyCondition();

  private DomFileDescription<T> myFileDescription;
  private final DomManagerImpl myDomManager;
  private int myModCount;

  private static final JBReentrantReadWriteLock rwl = LockFactory.createReadWriteLock();
  private static final JBLock r = rwl.readLock();
  protected static final JBLock w = rwl.writeLock();

  public FileDescriptionCachedValueProvider(final DomManagerImpl domManager, final XmlFile xmlFile) {
    myDomManager = domManager;
    myXmlFile = xmlFile;
    myCachedValue = xmlFile.getManager().getCachedValuesManager().createCachedValue(new MyCachedValueProvider(), false);
  }

  @Nullable
  public final DomFileDescription<T> getFileDescription() {
    r.lock();
    final DomFileDescription<T> description = myFileDescription;
    r.unlock();
    return description;
  }

  @Nullable
  public final DomFileElementImpl<T> getFileElement() {
    r.lock();
    try {
      if (myCachedValue.hasUpToDateValue()) return myLastResult;
    }
    finally {
      r.unlock();
    }

    final String rootTagName = getRootTag();
    w.lock();
    try {
      if (!myCachedValue.hasUpToDateValue()) {
        _computeFileElement(false, rootTagName);
      }
      return myLastResult;
    }
    finally {
      w.unlock();
    }
  }

  @NotNull
  public final List<DomEvent> computeFileElement(boolean fireEvents) {
    final String rootTagName = getRootTag();
    w.lock();
    try {
      return _computeFileElement(fireEvents, rootTagName);
    }
    finally {
      w.unlock();
    }
  }

  private List<DomEvent> _computeFileElement(final boolean fireEvents, final String rootTagName) {
    if (myComputing.get() != null || myDomManager.getProject().isDisposed()) return Collections.emptyList();
    myComputing.set(Boolean.TRUE);
    try {
      if (!myXmlFile.isValid()) {
        myModCount++;
        computeCachedValue(ArrayUtil.EMPTY_OBJECT_ARRAY);
        if (fireEvents && myLastResult != null) {
          removeFileElementFromCache(myLastResult, myFileDescription);
          myLastResult.resetRoot(true);
          return Arrays.<DomEvent>asList(new ElementUndefinedEvent(myLastResult));
        }
        return Collections.emptyList();
      }

      final Module module = ModuleUtil.findModuleForPsiElement(myXmlFile);
      if (lastResultSuits(rootTagName, module)) {
        List<DomEvent> list = new SmartList<DomEvent>();
        if (fireEvents) {
          list.add(new ElementChangedEvent(myLastResult));
        }
        myCachedValue.getValue();
        return list;
      }

      myModCount++;

      final Pair<DomFileDescription<T>,Object[]> description = findFileDescription(module, rootTagName);
      if (myCachedValue.hasUpToDateValue()) {
        return Collections.emptyList();
      }

      return saveResult(description.first, fireEvents, description.second);
    }
    finally {
      myComputing.set(null);
    }
  }

  private boolean lastResultSuits(final String rootTagName, final Module module) {
    if (myLastResult == null) return false;
    final DomFileDescription<T> description = myFileDescription;
    if (!description.getRootTagName().equals(rootTagName) && !description.acceptsOtherRootTagNames()) return false;
    w.unlock();
    try {
      return description.isMyFile(myXmlFile, module);
    }
    finally {
      w.lock();
    }
  }

  private MyCachedValueProvider getCachedValueProvider() {
    return ((MyCachedValueProvider)myCachedValue.getValueProvider());
  }

  @NotNull
  private Pair<DomFileDescription<T>,Object[]> findFileDescription(Module module, final String rootTagName) {
    final XmlFile originalFile = (XmlFile)myXmlFile.getOriginalFile();
    if (originalFile != null) {
      final FileDescriptionCachedValueProvider<T> provider = myDomManager.<T>getOrCreateCachedValueProvider(originalFile);
      provider.getFileElement();
      final Object[] dependencies = provider.getCachedValueProvider().dependencies;
      assert dependencies != null;
      return Pair.create(provider.getFileDescription(), dependencies);
    }

    myCondition.module = module;

    w.unlock();
    try {
      //noinspection unchecked
      DomFileDescription<T> description = ContainerUtil.find(myDomManager.getFileDescriptions(rootTagName), myCondition);
      if (description == null) {
        description = ContainerUtil.find(myDomManager.getAcceptingOtherRootTagNameDescriptions(), myCondition);
      }
      if (description == null) {
        return Pair.create(description, getAllDependencyItems());
      }
      final Set<Object> deps = new HashSet<Object>(description.getDependencyItems(myXmlFile));
      deps.add(this);
      deps.add(myXmlFile);
      return Pair.create(description, deps.toArray());
    }
    finally {
      w.lock();
    }
  }

  @Nullable
  private String getRootTag() {
    return myXmlFile.isValid() ? ourRootTagCache.get(ROOT_TAG_NS_KEY, myXmlFile, null).getValue() : null;
  }

  private List<DomEvent> saveResult(final DomFileDescription<T> description, final boolean fireEvents, Object[] dependencyItems) {
    final DomFileElementImpl oldValue = getLastValue();
    final DomFileDescription oldFileDescription = myFileDescription;
    final List<DomEvent> events = fireEvents ? new SmartList<DomEvent>() : Collections.<DomEvent>emptyList();
    if (oldValue != null) {
      assert oldFileDescription != null;
      removeFileElementFromCache(oldValue, oldFileDescription);
      oldValue.resetRoot(true);
      if (fireEvents) {
        events.add(new ElementUndefinedEvent(oldValue));
      }
    }

    myFileDescription = description;
    if (description == null) {
      myLastResult = null;
      computeCachedValue(dependencyItems);
      return events;
    }

    final Class<T> rootElementClass = description.getRootElementClass();
    final XmlName xmlName = DomImplUtil.createXmlName(description.getRootTagName(), rootElementClass, null);
    assert xmlName != null;
    final EvaluatedXmlNameImpl rootTagName = new EvaluatedXmlNameImpl(xmlName, xmlName.getNamespaceKey());
    myLastResult = new DomFileElementImpl<T>(myXmlFile, rootElementClass, rootTagName, myDomManager);
    computeCachedValue(dependencyItems);

    final Set<WeakReference<DomFileElementImpl>> references = myDomManager.getFileDescriptions().get(myFileDescription);
    references.add(new WeakReference<DomFileElementImpl>(myLastResult));
    if (fireEvents) {
      events.add(new ElementDefinedEvent(myLastResult));
    }
    return events;
  }

  private void removeFileElementFromCache(final DomFileElementImpl element, final DomFileDescription description) {
    final Set<WeakReference<DomFileElementImpl>> references = myDomManager.getFileDescriptions().get(description);
    for (Iterator<WeakReference<DomFileElementImpl>> iterator = references.iterator(); iterator.hasNext();) {
      final DomFileElementImpl fileElement = iterator.next().get();
      if (fileElement == null || fileElement == element) {
        iterator.remove();
        return;
      }
    }
  }

  private void computeCachedValue(@NotNull final Object[] dependencyItems) {
    getCachedValueProvider().dependencies = dependencyItems;
    myCachedValue.getValue();
  }

  @NotNull
  private Object[] getAllDependencyItems() {
    final Set<Object> deps = new LinkedHashSet<Object>();
    deps.add(this);
    deps.add(myXmlFile);
    Set<DomFileDescription<?>> domFileDescriptions = myDomManager.getFileDescriptions().keySet();
    for (final DomFileDescription<?> fileDescription : new HashSet<DomFileDescription<?>>(domFileDescriptions)) {
      deps.addAll(fileDescription.getDependencyItems(myXmlFile));
    }
    return deps.toArray();
  }

  @Nullable
  final DomFileElementImpl<T> getLastValue() {
    r.lock();
    final DomFileElementImpl<T> element = myLastResult;
    r.unlock();
    return element;
  }

  public long getModificationCount() {
    return myModCount;
  }

  private class MyCondition implements Condition<DomFileDescription> {
    public Module module;

    public boolean value(final DomFileDescription description) {
      return description.isMyFile(myXmlFile, module);
    }
  }

  private static class MyCachedValueProvider implements CachedValueProvider<Boolean> {
    public Object[] dependencies;

    public Result<Boolean> compute() {
      assert dependencies != null;
      return Result.create(Boolean.TRUE, dependencies);
    }
  }
}
