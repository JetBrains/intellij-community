/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.psi.impl;

import com.intellij.lang.Language;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.TransactionGuard;
import com.intellij.openapi.application.TransactionGuardImpl;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.openapi.util.SimpleModificationTracker;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.containers.ConcurrentFactoryMap;
import com.intellij.util.messages.MessageBus;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static com.intellij.psi.impl.PsiTreeChangeEventImpl.PsiEventType.CHILD_MOVED;
import static com.intellij.psi.impl.PsiTreeChangeEventImpl.PsiEventType.PROPERTY_CHANGED;

/**
 * @author mike
 * Date: Jul 18, 2002
 */
public class PsiModificationTrackerImpl implements PsiModificationTracker, PsiTreeChangePreprocessor {
  private final boolean myEnableCodeBlockTracker = Registry.is("psi.modification.tracker.code-block");
  private final boolean myEnableJavaStructureTracker = Registry.is("psi.modification.tracker.java-structure");
  private final boolean myEnableLanguageTracker = Registry.is("psi.modification.tracker.per-language");

  private final AtomicLong myModificationCount = new AtomicLong(0);
  private final AtomicLong myOutOfCodeBlockModificationCount = myEnableCodeBlockTracker ? new AtomicLong(0) : myModificationCount;
  private final AtomicLong myJavaStructureModificationCount = myEnableJavaStructureTracker ? new AtomicLong(0) : myModificationCount;

  private final ModificationTracker myOutOfCodeBlockModificationTracker = myEnableCodeBlockTracker ? () -> getOutOfCodeBlockModificationCount() : this;
  private final ModificationTracker myJavaStructureModificationTracker = myEnableJavaStructureTracker ? () -> getJavaStructureModificationCount() : this;

  private final Map<Language, ModificationTracker> myLanguageTrackers =
    ConcurrentFactoryMap.createMap(language -> new SimpleModificationTracker());

  private final Listener myPublisher;

  public PsiModificationTrackerImpl(Project project) {
    final MessageBus bus = project.getMessageBus();
    myPublisher = bus.syncPublisher(TOPIC);
    bus.connect().subscribe(DumbService.DUMB_MODE, new DumbService.DumbModeListener() {
      private void doIncCounter() {
        ApplicationManager.getApplication().runWriteAction(() -> incCounter());
      }

      @Override
      public void enteredDumbMode() {
        doIncCounter();
      }

      @Override
      public void exitDumbMode() {
        doIncCounter();
      }
    });
  }

  public void incCounter() {
    myModificationCount.getAndIncrement();
    myJavaStructureModificationCount.getAndIncrement();
    myOutOfCodeBlockModificationCount.getAndIncrement();
    fireEvent();
  }

  private void fireEvent() {
    ((TransactionGuardImpl)TransactionGuard.getInstance()).assertWriteActionAllowed();
    myPublisher.modificationCountChanged();
  }

  public void incOutOfCodeBlockModificationCounter() {
    myModificationCount.getAndIncrement();
    myOutOfCodeBlockModificationCount.getAndIncrement();
    fireEvent();
  }

  @Override
  public void treeChanged(@NotNull PsiTreeChangeEventImpl event) {
    if (!canAffectPsi(event)) {
      return;
    }

    incLanguageTrackers(event);

    PsiTreeChangeEventImpl.PsiEventType code = event.getCode();
    boolean outOfCodeBlock =
      code == PROPERTY_CHANGED ? event.getPropertyName() == PsiTreeChangeEvent.PROP_UNLOADED_PSI :
      code == CHILD_MOVED ? event.getOldParent() instanceof PsiDirectory || event.getNewParent() instanceof PsiDirectory :
      event.getParent() instanceof PsiDirectory;

    myModificationCount.getAndIncrement();
    if (outOfCodeBlock) {
      myJavaStructureModificationCount.getAndIncrement();
      myOutOfCodeBlockModificationCount.getAndIncrement();
    }
    fireEvent();
  }

  public static boolean canAffectPsi(@NotNull PsiTreeChangeEventImpl event) {
    return !PsiTreeChangeEvent.PROP_WRITABLE.equals(event.getPropertyName());
  }

  protected void incLanguageTrackers(@NotNull PsiTreeChangeEventImpl event) {
    if (!myEnableLanguageTracker) return;
    incLanguageModificationCount(Language.ANY);
    for (PsiElement o : new PsiElement[]{
      event.getFile(), event.getParent(), event.getOldParent(), event.getNewParent(),
      event.getElement(), event.getChild(), event.getOldChild(), event.getNewChild()
    }) {
      PsiFile file = o instanceof PsiFile ? (PsiFile)o : null;
      if (file == null) {
        try {
          IElementType type = PsiUtilCore.getElementType(o);
          Language language = type != null ? type.getLanguage() : o != null ? o.getLanguage() : null;
          incLanguageModificationCount(language);
        }
        catch (PsiInvalidElementAccessException e) {
          PsiDocumentManagerBase.LOG.warn(e);
        }
      }
      else {
        for (Language language : file.getViewProvider().getLanguages()) {
          incLanguageModificationCount(language);
        }
      }
    }
  }

  @Override
  public long getModificationCount() {
    return myModificationCount.get();
  }

  @Override
  public long getOutOfCodeBlockModificationCount() {
    return myOutOfCodeBlockModificationCount.get();
  }

  @NotNull
  @Override
  public ModificationTracker getOutOfCodeBlockModificationTracker() {
    return myOutOfCodeBlockModificationTracker;
  }

  @Override
  public long getJavaStructureModificationCount() {
    return myJavaStructureModificationCount.get();
  }

  @NotNull
  @Override
  public ModificationTracker getJavaStructureModificationTracker() {
    return myJavaStructureModificationTracker;
  }

  @ApiStatus.Experimental
  public void incLanguageModificationCount(@Nullable Language language) {
    if (language == null) return;
    ((SimpleModificationTracker)myLanguageTrackers.get(language)).incModificationCount();
  }

  @ApiStatus.Experimental
  @NotNull
  public ModificationTracker forLanguage(@NotNull Language language) {
    if (!myEnableLanguageTracker) return this;
    return myLanguageTrackers.get(language);
  }

  @ApiStatus.Experimental
  @NotNull
  public ModificationTracker forLanguages(@NotNull Condition<Language> condition) {
    if (!myEnableLanguageTracker) return this;
    return () -> {
      long result = 0;
      for (Language l : myLanguageTrackers.keySet()) {
        if (condition.value(l)) continue;
        result += myLanguageTrackers.get(l).getModificationCount();
      }
      return result;
    };
  }
}
