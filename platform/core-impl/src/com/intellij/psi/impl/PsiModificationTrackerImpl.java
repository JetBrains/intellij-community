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
import com.intellij.openapi.util.registry.RegistryValue;
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
 * Date: Jul 18, 2002
 */
public class PsiModificationTrackerImpl implements PsiModificationTracker, PsiTreeChangePreprocessor {
  private static final RegistryValue ourEnableCodeBlockTracker = Registry.get("psi.modification.tracker.code-block");
  private static final RegistryValue ourEnableJavaStructureTracker = Registry.get("psi.modification.tracker.java-structure");
  private static final RegistryValue ourEnableLanguageTracker = Registry.get("psi.modification.tracker.per-language");

  private final SimpleModificationTracker myModificationCount = new SimpleModificationTracker();
  private final SimpleModificationTracker myOutOfCodeBlockModificationTracker = wrapped(ourEnableCodeBlockTracker, myModificationCount);
  private final SimpleModificationTracker myJavaStructureModificationTracker = wrapped(ourEnableJavaStructureTracker, myModificationCount);

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
      code == PROPERTY_CHANGED ? event.getPropertyName() == PsiTreeChangeEvent.PROP_UNLOADED_PSI :
      code == CHILD_MOVED ? event.getOldParent() instanceof PsiDirectory || event.getNewParent() instanceof PsiDirectory :
      event.getParent() instanceof PsiDirectory;

    incCountersInner(outOfCodeBlock ? 7 : 1);
  }

  public static boolean canAffectPsi(@NotNull PsiTreeChangeEventImpl event) {
    return !PsiTreeChangeEvent.PROP_WRITABLE.equals(event.getPropertyName());
  }

  protected void incLanguageTrackers(@NotNull PsiTreeChangeEventImpl event) {
    if (!ourEnableLanguageTracker.asBoolean()) return;
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
  public boolean isEnableCodeBlockTracker() {
    return ourEnableCodeBlockTracker.asBoolean();
  }

  @ApiStatus.Experimental
  public boolean isEnableLanguageTracker() {
    return ourEnableLanguageTracker.asBoolean();
  }

  @ApiStatus.Experimental
  public void incLanguageModificationCount(@Nullable Language language) {
    if (language == null) return;
    ((SimpleModificationTracker)myLanguageTrackers.get(language)).incModificationCount();
  }

  @ApiStatus.Experimental
  @NotNull
  public ModificationTracker forLanguage(@NotNull Language language) {
    if (!ourEnableLanguageTracker.asBoolean()) return this;
    return myLanguageTrackers.get(language);
  }

  @ApiStatus.Experimental
  @NotNull
  public ModificationTracker forLanguages(@NotNull Condition<Language> condition) {
    if (!ourEnableLanguageTracker.asBoolean()) return this;
    return () -> {
      long result = 0;
      for (Language l : myLanguageTrackers.keySet()) {
        if (condition.value(l)) continue;
        result += myLanguageTrackers.get(l).getModificationCount();
      }
      return result;
    };
  }

  @NotNull
  private static SimpleModificationTracker wrapped(RegistryValue value, SimpleModificationTracker fallback) {
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      return new SimpleModificationTracker();
    }
    return new SimpleModificationTracker() {
      @Override
      public long getModificationCount() {
        return value.asBoolean() ? super.getModificationCount() :
               fallback.getModificationCount();
      }

      @Override
      public void incModificationCount() {
        if (value.asBoolean()) super.incModificationCount();
        //else fallback.incModificationCount();
      }
    };
  }
}
