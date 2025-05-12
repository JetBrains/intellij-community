// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.impl;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.CollectionFactory;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.WeakList;
import kotlinx.coroutines.Job;
import org.jetbrains.annotations.*;

import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CancellationException;

public final class ModalityStateEx extends ModalityState {

  private final WeakList<Object> myModalEntities = new WeakList<>();
  private static final Set<Object> ourTransparentEntities = Collections.newSetFromMap(CollectionFactory.createConcurrentWeakMap());

  @SuppressWarnings("unused")
  public ModalityStateEx() { } // used by reflection to initialize NON_MODAL

  @VisibleForTesting
  @ApiStatus.Internal
  public ModalityStateEx(@NotNull List<?> modalEntities) {
    if (modalEntities.contains(null)) {
      throw new IllegalArgumentException("Must not pass null modality: " + modalEntities);
    }
    myModalEntities.addAll(modalEntities);
  }

  private @NotNull @Unmodifiable List<@NotNull Object> getModalEntities() {
    return myModalEntities.toStrongList();
  }

  public @NotNull ModalityState appendProgress(@NotNull ProgressIndicator progress) {
    return appendEntity(progress);
  }

  public @NotNull ModalityState appendJob(@NotNull Job job) {
    return appendEntity(job);
  }

  @NotNull ModalityStateEx appendEntity(@NotNull Object anEntity) {
    List<@NotNull Object> modalEntities = getModalEntities();
    List<Object> list = new ArrayList<>(modalEntities.size() + 1);
    list.addAll(modalEntities);
    list.add(anEntity);
    return new ModalityStateEx(list);
  }

  void forceModalEntities(@NotNull ModalityStateEx other) {
    List<@NotNull Object> otherEntities = other.getModalEntities();
    myModalEntities.clear();
    myModalEntities.addAll(otherEntities);
  }

  @Override
  public boolean accepts(@NotNull ModalityState requestedModality) {
    /*
    modality1 {
      modality2 {
        invokeLater(modality2) {
          // in next lines we are considering when this lambda is allowed to run
        }
        // Trivial case:
        // this/current = [2,1]
        // requested = [2,1]
        // => OK.

        modality3 {
          // this/current = [3,2,1]
          // requested = [2,1]
          // => not OK because this is not a subset of requested
        }
      }

      // this/current = [1] (2 had already ended)
      // requested = [2,1]
      // => OK.

      modality4 {
        // this/current = [4,1]
        // requested = [2,1]
        // => not OK because this is not a subset of requested
      }
    }
    */
    if (requestedModality == any()) {
      // Tasks with any modality can be run during this modality regardless of entities in this modality.
      return true;
    }
    if (requestedModality == this) {
      // Trivial case.
      return true;
    }
    // All my entities are present in requested (computation) modality,
    // i.e., requested modality is strictly nested in this (current) modality.
    return ((ModalityStateEx)requestedModality).myModalEntities.containsAll(
      myModalEntities,
      entity -> !ourTransparentEntities.contains(entity)
    );
  }

  void cancelAllEntities(@NonNls String reason) {
    for (Object entity : myModalEntities) {
      // DialogWrapperDialog is not accessible here
      if (entity instanceof Dialog) {
        Dialog dialog = (Dialog)entity;
        Logger.getInstance(ModalityStateEx.class).info("Closing the dialog " + dialog + ". Cause: " + reason);

        dialog.setVisible(false);
        if (dialog instanceof Disposable) { // see com.intellij.openapi.ui.impl.AbstractDialog
          Disposer.dispose((Disposable)dialog);
        }
      }
      else if (entity instanceof ProgressIndicator) {
        ProgressIndicator indicator = (ProgressIndicator)entity;
        if (!indicator.isCanceled()) {
          Logger.getInstance(ModalityStateEx.class).info("Cancelling indicator " + indicator + ". Cause: " + reason);
          indicator.cancel();
        }
      }
      else if (entity instanceof JobProvider) {
        JobProvider jobProvider = (JobProvider)entity;
        Job job = jobProvider.getJob();
        if (!job.isCancelled()) {
          Logger.getInstance(ModalityStateEx.class).info("Cancelling job " + jobProvider + ". Cause: " + reason);
          job.cancel(new CancellationException("force leave modal"));
        }
      }
    }
  }

  @Override
  @SuppressWarnings("deprecation")
  public @NonNls String toString() {
    return this == NON_MODAL
           ? "ModalityState.NON_MODAL"
           : "ModalityState:{" + StringUtil.join(getModalEntities(), it -> "[" + it + "]", ", ") + "}";
  }

  void removeModality(@NotNull Object modalEntity) {
    myModalEntities.remove(modalEntity);
  }

  void markTransparent() {
    ContainerUtil.addIfNotNull(ourTransparentEntities, ContainerUtil.getLastItem(getModalEntities()));
  }

  static void unmarkTransparent(@NotNull Object modalEntity) {
    ourTransparentEntities.remove(modalEntity);
  }

  boolean contains(@NotNull Object modalEntity) {
    return getModalEntities().contains(modalEntity);
  }
}
