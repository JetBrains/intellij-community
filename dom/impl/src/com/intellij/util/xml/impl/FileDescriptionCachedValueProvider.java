/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.util.xml.impl;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.psi.PsiLock;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashSet;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomFileDescription;
import com.intellij.util.xml.events.ElementDefinedEvent;
import com.intellij.util.xml.events.ElementUndefinedEvent;
import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.LinkedHashSet;

/**
 * @author peter
*/
class FileDescriptionCachedValueProvider<T extends DomElement> implements CachedValueProvider<DomFileElementImpl<T>>, ModificationTracker {
  private Module myModule;
  private final XmlFile myXmlFile;
  private Runnable myPostRunnable;
  private boolean myFireEvents;
  private boolean myInModel;
  private Result<DomFileElementImpl<T>> myOldResult;
  private long myModCount;
  private final Condition<DomFileDescription> myCondition = new Condition<DomFileDescription>() {
    public boolean value(final DomFileDescription description) {
      return description.isMyFile(myXmlFile, myModule);
    }
  };

  private DomFileDescription<T> myFileDescription;
  private DomManagerImpl myDomManager;

  public FileDescriptionCachedValueProvider(final DomManagerImpl domManager, final XmlFile xmlFile) {
    myDomManager = domManager;
    myXmlFile = xmlFile;
  }

  public final DomFileDescription getFileDescription() {
    return myFileDescription;
  }

  final void fireEvents() {
    final Runnable postRunnable = myPostRunnable;
    if (postRunnable != null) {
      postRunnable.run();
      myPostRunnable = null;
    }
  }

  final void setFireEvents(final boolean b) {
    myFireEvents = b;
  }

  public Result<DomFileElementImpl<T>> compute() {
    synchronized (PsiLock.LOCK) {
      if (myDomManager.getProject().isDisposed()) return new Result<DomFileElementImpl<T>>(null);
      final boolean fireEvents = myFireEvents;
      myFireEvents = false;

      myModule = ModuleUtil.findModuleForPsiElement(myXmlFile);
      final String rootTagName = getRootTagName();
      if (myOldResult != null && myFileDescription != null && myFileDescription.getRootTagName().equals(rootTagName) && myFileDescription.isMyFile(myXmlFile, myModule)) {
        return myOldResult;
      }

      final XmlFile originalFile = (XmlFile)myXmlFile.getOriginalFile();
      if (originalFile != null) {
        return saveResult(myDomManager.getOrCreateCachedValueProvider(originalFile).getFileDescription(), fireEvents);
      }

      return saveResult(findFileDescription(rootTagName), fireEvents);
    }
  }

  @Nullable
  private DomFileDescription findFileDescription(final String rootTagName) {
    if (rootTagName != null) {
      final DomFileDescription description = ContainerUtil.find(myDomManager.getFileDescriptions(rootTagName), myCondition);
      if (description != null) {
        return description;
      }
    }
    return ContainerUtil.find(myDomManager.getAcceptingOtherRootTagNameDescriptions(rootTagName), myCondition);
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
    return (String)null;
  }

  final boolean isInModel() {
    return myInModel;
  }

  final void setInModel(final boolean inModel) {
    myInModel = inModel;
  }

  private Result<DomFileElementImpl<T>> saveResult(final DomFileDescription<T> description, final boolean fireEvents) {
    myInModel = false;
    final DomFileElementImpl oldValue = getOldValue();
    final DomFileDescription oldFileDescription = myFileDescription;
    final Runnable undefinedRunnable = new Runnable() {
      public void run() {
        if (oldValue != null) {
          assert oldFileDescription != null;
          myDomManager.getFileDescriptions().get(oldFileDescription).remove(oldValue);
          if (fireEvents) {
            myDomManager.fireEvent(new ElementUndefinedEvent(oldValue), false);
          }
        }
      }
    };

    myFileDescription = description;
    if (description == null) {
      myPostRunnable = undefinedRunnable;
      myOldResult = null;
      return new Result<DomFileElementImpl<T>>(null, getAllDependencyItems());
    }

    final DomFileElementImpl<T> fileElement = myDomManager.createFileElement(myXmlFile, description);

    myPostRunnable = new Runnable() {
      public void run() {
        undefinedRunnable.run();
        myDomManager.getFileDescriptions().get(myFileDescription).add(fileElement);
        if (fireEvents) {
          myDomManager.fireEvent(new ElementDefinedEvent(fileElement), false);
        }
      }
    };

    final Set<Object> deps = new HashSet<Object>(description.getDependencyItems(myXmlFile));
    deps.add(myXmlFile);
    deps.add(this);
    return myOldResult = new Result<DomFileElementImpl<T>>(fileElement, deps.toArray());
  }

  private Object[] getAllDependencyItems() {
    //return myLostDependency;
    final Set<Object> deps = new LinkedHashSet<Object>();
    deps.add(PsiModificationTracker.OUT_OF_CODE_BLOCK_MODIFICATION_COUNT);
    deps.add(this);
    for (final DomFileDescription<?> fileDescription : myDomManager.getFileDescriptions().keySet()) {
      deps.addAll(fileDescription.getDependencyItems(myXmlFile));
    }
    return deps.toArray();
  }

  //private final Object[] myLostDependency = new Object[] { PsiModificationTracker.OUT_OF_CODE_BLOCK_MODIFICATION_COUNT };

  @Nullable
  final DomFileElementImpl getOldValue() {
    return myOldResult != null ? myOldResult.getValue() : null;
  }

  public final void changed() {
    myModCount++;
    setFireEvents(true);
  }

  public long getModificationCount() {
    return myModCount;
  }
}
