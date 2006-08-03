/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.util.xml.impl;

import com.intellij.openapi.util.Condition;
import com.intellij.psi.PsiLock;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xml.DomFileDescription;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.events.ElementDefinedEvent;
import com.intellij.util.xml.events.ElementUndefinedEvent;
import gnu.trove.THashSet;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Set;

/**
 * @author peter
*/
class FileDescriptionCachedValueProvider<T extends DomElement> implements CachedValueProvider<DomFileElementImpl<T>> {
  private final XmlFile myXmlFile;
  private Runnable myPostRunnable;
  private Result<DomFileElementImpl<T>> myOldResult;
  private final Condition<DomFileDescription> myCondition = new Condition<DomFileDescription>() {
    public boolean value(final DomFileDescription description) {
      return description.isMyFile(myXmlFile);
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
    if (myPostRunnable != null) {
      myPostRunnable.run();
      myPostRunnable = null;
    }
  }

  public Result<DomFileElementImpl<T>> compute() {
    synchronized (PsiLock.LOCK) {
      if (myDomManager.getProject().isDisposed()) return new Result<DomFileElementImpl<T>>(null);

      if (myOldResult != null && myFileDescription != null && myFileDescription.isMyFile(myXmlFile)) {
        return myOldResult;
      }

      final XmlFile originalFile = (XmlFile)myXmlFile.getOriginalFile();
      if (originalFile != null) {
        return saveResult(myDomManager.getOrCreateCachedValueProvider(originalFile).getFileDescription());
      }

      return saveResult(ContainerUtil.find(myDomManager.getFileDescriptions().keySet(), myCondition));
    }
  }

  private Result<DomFileElementImpl<T>> saveResult(final DomFileDescription<T> description) {
    final DomFileElementImpl oldValue = getOldValue();
    final DomFileDescription oldFileDescription = myFileDescription;
    final Runnable undefinedRunnable = new Runnable() {
      public void run() {
        if (oldValue != null) {
          assert oldFileDescription != null;
          myDomManager.getFileDescriptions().get(oldFileDescription).remove(oldValue);
          myDomManager.fireEvent(new ElementUndefinedEvent(oldValue), false);
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
        myDomManager.getFileDescriptions().get(myFileDescription).add(oldValue);
        myDomManager.fireEvent(new ElementDefinedEvent(fileElement), false);
      }
    };

    return myOldResult = new Result<DomFileElementImpl<T>>(fileElement, description.getDependencyItems(myXmlFile));
  }

  private Object[] getAllDependencyItems() {
    final Set<Object> deps = new THashSet<Object>();
    deps.add(myXmlFile);
    for (final DomFileDescription fileDescription : myDomManager.getFileDescriptions().keySet()) {
      deps.addAll(Arrays.asList(fileDescription.getDependencyItems(myXmlFile)));
    }
    return deps.toArray();
  }

  @Nullable
  private DomFileElementImpl getOldValue() {
    return myOldResult != null ? myOldResult.getValue() : null;
  }
}
