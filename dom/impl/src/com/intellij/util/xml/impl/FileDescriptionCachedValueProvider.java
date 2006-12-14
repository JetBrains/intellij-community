/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.util.xml.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiManager;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.ArrayUtil;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashSet;
import com.intellij.util.text.CharSequenceReader;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomFileDescription;
import com.intellij.util.xml.DomFileElement;
import com.intellij.util.xml.events.DomEvent;
import com.intellij.util.xml.events.ElementChangedEvent;
import com.intellij.util.xml.events.ElementDefinedEvent;
import com.intellij.util.xml.events.ElementUndefinedEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.xml.sax.InputSource;

import java.io.IOException;
import java.util.*;

/**
 * @author peter
 */
class FileDescriptionCachedValueProvider<T extends DomElement> implements ModificationTracker {
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.xml.impl.FileDescriptionCachedValueProvider");
  private final XmlFile myXmlFile;
  private boolean myInModel;
  private boolean myComputing;
  private DomFileElementImpl<T> myLastResult;
  private final CachedValue<Boolean> myCachedValue;
  private final MyCondition myCondition = new MyCondition();

  private DomFileDescription<T> myFileDescription;
  private final DomManagerImpl myDomManager;
  private int myModCount;
  private static final int LIMIT = 17239;
  private static final int START = 1024;

  public FileDescriptionCachedValueProvider(final DomManagerImpl domManager, final XmlFile xmlFile) {
    myDomManager = domManager;
    myXmlFile = xmlFile;
    myCachedValue = PsiManager.getInstance(domManager.getProject()).getCachedValuesManager().createCachedValue(new MyCachedValueProvider(), false);
  }

  @Nullable
  public final DomFileDescription getFileDescription() {
    return myFileDescription;
  }

  @Nullable
  public final DomFileElementImpl<T> getFileElement() {
    if (!myCachedValue.hasUpToDateValue()) {
      computeFileElement(false, null);
    }
    return myLastResult;
  }

  @NotNull
  public final List<DomEvent> computeFileElement(boolean fireEvents, @Nullable DomFileElement changedRoot) {
    if (myComputing || myDomManager.getProject().isDisposed()) return Collections.emptyList();
    myComputing = true;
    try {
      if (!myXmlFile.isValid()) {
        myModCount++;
        computeCachedValue(ArrayUtil.EMPTY_OBJECT_ARRAY);
        if (fireEvents && myLastResult != null) {
          myDomManager.getFileDescriptions().get(myFileDescription).remove(myLastResult);
          myLastResult.resetRoot(true);
          return Arrays.<DomEvent>asList(new ElementUndefinedEvent(myLastResult));
        }
        return Collections.emptyList();
      }

      final Module module = ModuleUtil.findModuleForPsiElement(myXmlFile);
      if (myLastResult != null && myFileDescription.getRootTagName().equals(getRootTagName()) && myFileDescription.isMyFile(myXmlFile, module)) {
        List<DomEvent> list = new SmartList<DomEvent>();
        setInModel(changedRoot, list, myLastResult, fireEvents);
        myCachedValue.getValue();
        return list;
      }

      myModCount++;
      final XmlFile originalFile = (XmlFile)myXmlFile.getOriginalFile();
      final DomFileDescription<T> description = originalFile != null
                                                ? myDomManager.getOrCreateCachedValueProvider(originalFile).getFileDescription()
                                                : findFileDescription(module);
      return saveResult(description, fireEvents, changedRoot);
    }
    finally {
      myComputing = false;
    }
  }

  private MyCachedValueProvider getCachedValueProvider() {
    return ((MyCachedValueProvider)myCachedValue.getValueProvider());
  }

  @NotNull
  private InputSource getInputStream() throws IOException {
    final FileViewProvider viewProvider = myXmlFile.getViewProvider();
    final VirtualFile file = viewProvider.getVirtualFile();
    final Document document = PsiDocumentManager.getInstance(myXmlFile.getProject()).getCachedDocument(myXmlFile);
    if (document != null) {
      return new InputSource(new CharSequenceReader(document.getCharsSequence()));
    }

    if (!viewProvider.isPhysical()) {
      return new InputSource(new CharSequenceReader(myXmlFile.getText()));
    }
    return new InputSource(file.getInputStream());
  }

  @Nullable
  private DomFileDescription<T> findFileDescription(Module module) {
    myCondition.module = module;
    try {
      final Pair<String,String> pair = DomImplUtil.getRootTagAndNamespace(getInputStream());
      final DomFileDescription<T> description = ContainerUtil.find(myDomManager.getFileDescriptions(pair.first), myCondition);
      if (description != null) {
        return description;
      }
      return ContainerUtil.find(myDomManager.getAcceptingOtherRootTagNameDescriptions(), myCondition);
    }
    catch (IOException e) {
      return null;
    }
  }

  @Nullable
  private String getRootTagName() {
    final XmlDocument document = myXmlFile.getDocument();
    if (document != null) {
      final XmlTag tag = document.getRootTag();
      if (tag != null) {
        return tag.getLocalName();
      }
    }
    return null;
  }

  private List<DomEvent> saveResult(final DomFileDescription<T> description, final boolean fireEvents, DomFileElement changedRoot) {
    final DomFileElementImpl oldValue = getLastValue();
    final DomFileDescription oldFileDescription = myFileDescription;
    final List<DomEvent> events = fireEvents ? new SmartList<DomEvent>() : Collections.<DomEvent>emptyList();
    if (oldValue != null) {
      assert oldFileDescription != null;
      myDomManager.getFileDescriptions().get(oldFileDescription).remove(oldValue);
      oldValue.resetRoot(true);
      if (fireEvents) {
        events.add(new ElementUndefinedEvent(oldValue));
      }
    }

    myFileDescription = description;
    myLastResult = description == null ? null : new DomFileElementImpl<T>(myXmlFile, description.getRootElementClass(),
                                                                          XmlName.create(description.getRootTagName(), description.getRootElementClass()).createEvaluatedXmlName(null), myDomManager);
    setInModel(changedRoot, events, oldValue, fireEvents);
    if (description == null) {
      computeCachedValue(getAllDependencyItems());
      return events;
    }

    final Set<Object> deps = new HashSet<Object>(description.getDependencyItems(myXmlFile));
    deps.add(this);
    deps.add(myXmlFile);
    final Object[] dependencyItems = deps.toArray();
    computeCachedValue(dependencyItems);

    myDomManager.getFileDescriptions().get(myFileDescription).add(myLastResult);
    if (fireEvents) {
      events.add(new ElementDefinedEvent(myLastResult));
    }
    return events;
  }

  private void setInModel(final DomFileElement changedRoot, final List<DomEvent> events, final DomFileElementImpl oldValue, final boolean fireEvents) {
    boolean wasInModel = oldValue != null && myInModel;
    myInModel = myFileDescription != null && (changedRoot == null || myFileDescription.getDomModelDependentFiles(changedRoot).contains(myXmlFile));
    if (fireEvents && oldValue != null && myLastResult != null) {
      if (oldValue.equals(myLastResult)) events.add(new ElementChangedEvent(myLastResult));
      else if (!myInModel && wasInModel) events.add(new ElementChangedEvent(oldValue));
      else if (myInModel && !wasInModel) events.add(new ElementChangedEvent(myLastResult));
    }
  }

  private void computeCachedValue(final Object[] dependencyItems) {
    assert !myCachedValue.hasUpToDateValue();
    getCachedValueProvider().dependencies = dependencyItems;
    myCachedValue.getValue();
  }

  private Object[] getAllDependencyItems() {
    final Set<Object> deps = new LinkedHashSet<Object>();
    deps.add(PsiModificationTracker.OUT_OF_CODE_BLOCK_MODIFICATION_COUNT);
    deps.add(this);
    deps.add(myXmlFile);
    for (final DomFileDescription<?> fileDescription : myDomManager.getFileDescriptions().keySet()) {
      deps.addAll(fileDescription.getDependencyItems(myXmlFile));
    }
    return deps.toArray();
  }

  @Nullable
  final DomFileElementImpl<T> getLastValue() {
    return myLastResult;
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
