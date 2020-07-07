// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl;

import com.intellij.lang.Language;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.TransactionGuard;
import com.intellij.openapi.application.TransactionGuardImpl;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.openapi.util.SimpleModificationTracker;
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
import java.util.function.Predicate;

import static com.intellij.psi.impl.PsiTreeChangeEventImpl.PsiEventType.*;

public final class PsiModificationTrackerImpl implements PsiModificationTracker, PsiTreeChangePreprocessor {
  private final SimpleModificationTracker myModificationCount = new SimpleModificationTracker();

  private final SimpleModificationTracker myAllLanguagesTracker = new SimpleModificationTracker();
  private final Map<Language, SimpleModificationTracker> myLanguageTrackers =
    ConcurrentFactoryMap.createWeakMap(language -> new SimpleModificationTracker());
  private final Listener myPublisher;

  public PsiModificationTrackerImpl(@NotNull Project project) {
    MessageBus bus = project.getMessageBus();
    myPublisher = bus.syncPublisher(TOPIC);
    bus.connect().subscribe(DumbService.DUMB_MODE, new DumbService.DumbModeListener() {
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

  private void doIncCounter() {
    ApplicationManager.getApplication().runWriteAction(() -> incCounter());
  }

  /**
   * @deprecated use higher-level ways of dropping caches
   * @see com.intellij.util.FileContentUtilCore#reparseFiles
   * @see PsiManager#dropPsiCaches()
   */
  @ApiStatus.Internal
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.1")
  public void incCounter() {
    ApplicationManager.getApplication().assertWriteAccessAllowed();
    incCountersInner();
  }

  public void incOutOfCodeBlockModificationCounter() {
    incCountersInner();
  }

  private void fireEvent() {
    ((TransactionGuardImpl)TransactionGuard.getInstance()).assertWriteActionAllowed();
    myPublisher.modificationCountChanged();
  }

  private void incCountersInner() {
    myModificationCount.incModificationCount();
    fireEvent();
  }

  @Override
  public void treeChanged(@NotNull PsiTreeChangeEventImpl event) {
    if (!canAffectPsi(event)) {
      return;
    }

    incLanguageCounters(event);
    incCountersInner();
  }

  // used by Kotlin
  @SuppressWarnings("WeakerAccess")
  public static boolean canAffectPsi(@NotNull PsiTreeChangeEventImpl event) {
    PsiTreeChangeEventImpl.PsiEventType code = event.getCode();
    return !(code == BEFORE_PROPERTY_CHANGE ||
             code == PROPERTY_CHANGED && event.getPropertyName() == PsiTreeChangeEvent.PROP_WRITABLE);
  }

  private void incLanguageCounters(@NotNull PsiTreeChangeEventImpl event) {
    PsiTreeChangeEventImpl.PsiEventType code = event.getCode();
    String propertyName = event.getPropertyName();

    if (code == PROPERTY_CHANGED &&
        (propertyName == PsiTreeChangeEvent.PROP_UNLOADED_PSI ||
         propertyName == PsiTreeChangeEvent.PROP_ROOTS ||
         propertyName == PsiTreeChangeEvent.PROP_FILE_TYPES) ||
        code == CHILD_REMOVED && event.getChild() instanceof PsiDirectory) {
      myAllLanguagesTracker.incModificationCount();
      return;
    }
    PsiElement[] elements = {
      event.getFile(), event.getParent(), event.getOldParent(), event.getNewParent(),
      event.getElement(), event.getChild(), event.getOldChild(), event.getNewChild()};
    incLanguageModificationCount(Language.ANY);
    for (PsiElement o : elements) {
      if (o == null) continue;
      if (o instanceof PsiDirectory) continue;
      if (o instanceof PsiFile) {
        for (Language language : ((PsiFile)o).getViewProvider().getLanguages()) {
          incLanguageModificationCount(language);
        }
      }
      else {
        try {
          IElementType type = PsiUtilCore.getElementType(o);
          Language language = type != null ? type.getLanguage() : o.getLanguage();
          incLanguageModificationCount(language);
        }
        catch (PsiInvalidElementAccessException e) {
          PsiDocumentManagerBase.LOG.warn(e);
        }
      }
    }
  }

  @Override
  public long getModificationCount() {
    return myModificationCount.getModificationCount();
  }

  @Override
  public long getOutOfCodeBlockModificationCount() {
    return myModificationCount.getModificationCount();
  }

  @Override
  public long getJavaStructureModificationCount() {
    return myModificationCount.getModificationCount();
  }

  @Override
  public @NotNull ModificationTracker getOutOfCodeBlockModificationTracker() {
    return myModificationCount;
  }

  @Override
  public @NotNull ModificationTracker getJavaStructureModificationTracker() {
    return myModificationCount;
  }

  // used by Kotlin
  @SuppressWarnings("WeakerAccess")
  @ApiStatus.Experimental
  public void incLanguageModificationCount(@Nullable Language language) {
    if (language == null) return;
    myLanguageTrackers.get(language).incModificationCount();
  }

  @ApiStatus.Experimental
  public @NotNull ModificationTracker forLanguage(@NotNull Language language) {
    SimpleModificationTracker languageTracker = myLanguageTrackers.get(language);
    return () -> languageTracker.getModificationCount() +
                 myAllLanguagesTracker.getModificationCount();
  }

  @ApiStatus.Experimental
  public @NotNull ModificationTracker forLanguages(@NotNull Predicate<? super Language> condition) {
    return () -> {
      long result = myAllLanguagesTracker.getModificationCount();
      for (Language l : myLanguageTrackers.keySet()) {
        if (!condition.test(l)) continue;
        result += myLanguageTrackers.get(l).getModificationCount();
      }
      return result;
    };
  }
}
