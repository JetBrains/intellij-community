// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

import static com.intellij.psi.impl.PsiTreeChangeEventImpl.PsiEventType.CHILD_MOVED;
import static com.intellij.psi.impl.PsiTreeChangeEventImpl.PsiEventType.PROPERTY_CHANGED;

/**
 * @author mike
 */
public class PsiModificationTrackerImpl implements PsiModificationTracker, PsiTreeChangePreprocessor {

  private final SimpleModificationTracker myModificationCount = new SimpleModificationTracker();
  private final SimpleModificationTracker myOutOfCodeBlockModificationTracker = myModificationCount;
  private final SimpleModificationTracker myJavaStructureModificationTracker = myModificationCount;

  private final Map<Language, ModificationTracker> myLanguageTrackers =
    ConcurrentFactoryMap.createMap(language -> new SimpleModificationTracker());

  private final Listener myPublisher;

  public PsiModificationTrackerImpl(Project project) {
    MessageBus bus = project.getMessageBus();
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
    incCountersInner(7);
  }

  public void incOutOfCodeBlockModificationCounter() {
    incCountersInner(3);
  }

  private void fireEvent() {
    ((TransactionGuardImpl)TransactionGuard.getInstance()).assertWriteActionAllowed();
    myPublisher.modificationCountChanged();
  }

  private void incCountersInner(int bits) {
    if ((bits & 0x1) != 0) myModificationCount.incModificationCount();
    if ((bits & 0x2) != 0) myOutOfCodeBlockModificationTracker.incModificationCount();
    if ((bits & 0x4) != 0) myJavaStructureModificationTracker.incModificationCount();
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
      code == PROPERTY_CHANGED ? event.getPropertyName() == PsiTreeChangeEvent.PROP_UNLOADED_PSI || event.getPropertyName() == PsiTreeChangeEvent.PROP_ROOTS :
      code == CHILD_MOVED ? event.getOldParent() instanceof PsiDirectory || event.getNewParent() instanceof PsiDirectory :
      event.getParent() instanceof PsiDirectory;

    incCountersInner(outOfCodeBlock ? 7 : 1);
  }

  public static boolean canAffectPsi(@NotNull PsiTreeChangeEventImpl event) {
    return !PsiTreeChangeEvent.PROP_WRITABLE.equals(event.getPropertyName());
  }

  protected void incLanguageTrackers(@NotNull PsiTreeChangeEventImpl event) {
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
    return myOutOfCodeBlockModificationTracker.getModificationCount();
  }

  @Override
  public long getJavaStructureModificationCount() {
    return myJavaStructureModificationTracker.getModificationCount();
  }

  @NotNull
  @Override
  public ModificationTracker getOutOfCodeBlockModificationTracker() {
    return myOutOfCodeBlockModificationTracker;
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
    return myLanguageTrackers.get(language);
  }

  @ApiStatus.Experimental
  @NotNull
  public ModificationTracker forLanguages(@NotNull Condition<? super Language> condition) {
    return () -> {
      long result = 0;
      for (Language l : myLanguageTrackers.keySet()) {
        if (!condition.value(l)) continue;
        result += myLanguageTrackers.get(l).getModificationCount();
      }
      return result;
    };
  }
}
