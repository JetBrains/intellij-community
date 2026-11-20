// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.modcommand;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiPlace;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Shows a UI that displays conflicts and requires user confirmation to proceed. Not executed in batch; skipped in preview.
 *
 * @param conflictsByPlace conflicts to show, each at its place
 */
public record ModShowConflicts(@NotNull @Unmodifiable Map<@NotNull PsiPlace, @NotNull Conflict> conflictsByPlace) implements ModCommand {
  public ModShowConflicts {
    conflictsByPlace = Collections.unmodifiableMap(new LinkedHashMap<>(conflictsByPlace));
  }

  /**
   * @return the conflicts per element; the places of one element merge, and their ranges are lost
   */
  public @NotNull @Unmodifiable Map<@NotNull PsiElement, @NotNull Conflict> conflicts() {
    Map<PsiElement, Conflict> conflicts = new LinkedHashMap<>();
    conflictsByPlace.forEach((place, conflict) -> conflicts.merge(place.element(), conflict, Conflict::merge));
    return Collections.unmodifiableMap(conflicts);
  }

  /**
   * Conflict description
   * @param messages list of user-readable messages that describe the problem
   */
  public record Conflict(@NotNull List<@NotNull @Nls String> messages) {
    /**
     * @param conflict another set of conflicts
     * @return the merged set of conflicts that contains information from this and supplied conflict collections
     */
    public @NotNull Conflict merge(@NotNull Conflict conflict) {
      return new Conflict(Stream.concat(messages.stream(), conflict.messages.stream()).distinct().toList());
    }
  }

  @Override
  public boolean isEmpty() {
    return conflictsByPlace.isEmpty();
  }
}
