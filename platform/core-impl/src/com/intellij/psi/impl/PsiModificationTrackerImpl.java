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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.TransactionGuard;
import com.intellij.openapi.application.TransactionGuardImpl;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiTreeChangeEvent;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.util.messages.MessageBus;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.atomic.AtomicLong;

import static com.intellij.psi.impl.PsiTreeChangeEventImpl.PsiEventType.CHILD_MOVED;
import static com.intellij.psi.impl.PsiTreeChangeEventImpl.PsiEventType.PROPERTY_CHANGED;

/**
 * @author mike
 * Date: Jul 18, 2002
 */
public class PsiModificationTrackerImpl implements PsiModificationTracker, PsiTreeChangePreprocessor {
  private final AtomicLong myModificationCount = new AtomicLong(0);
  private final AtomicLong myOutOfCodeBlockModificationCount = new AtomicLong(0);
  private final AtomicLong myJavaStructureModificationCount = new AtomicLong(0);
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

  @Override
  public long getModificationCount() {
    return myModificationCount.get();
  }

  @Override
  public long getOutOfCodeBlockModificationCount() {
    return myOutOfCodeBlockModificationCount.get();
  }

  private final ModificationTracker myOutOfCodeBlockModificationTracker = new ModificationTracker() {
    @Override
    public long getModificationCount() {
      return getOutOfCodeBlockModificationCount();
    }
  };

  @NotNull
  @Override
  public ModificationTracker getOutOfCodeBlockModificationTracker() {
    return myOutOfCodeBlockModificationTracker;
  }

  @Override
  public long getJavaStructureModificationCount() {
    return myJavaStructureModificationCount.get();
  }

  private final ModificationTracker myJavaStructureModificationTracker = new ModificationTracker() {
    @Override
    public long getModificationCount() {
      return getJavaStructureModificationCount();
    }
  };
  @NotNull
  @Override
  public ModificationTracker getJavaStructureModificationTracker() {
    return myJavaStructureModificationTracker;
  }
}
