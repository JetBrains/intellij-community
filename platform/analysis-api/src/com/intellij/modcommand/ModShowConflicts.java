// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.modcommand;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Shows a UI that displays conflicts and requires user confirmation to proceed. Not executed in batch; skipped in preview.
 *
 * @param places conflicts to show, each at its place
 */
public record ModShowConflicts(@NotNull @Unmodifiable List<@NotNull Place> places) implements ModCommand {
  public ModShowConflicts {
    places = List.copyOf(places);
  }

  /**
   * @param conflicts conflicts to show, each at the whole element
   */
  public ModShowConflicts(@NotNull Map<@NotNull PsiElement, @NotNull Conflict> conflicts) {
    this(ContainerUtil.map(conflicts.entrySet(), entry -> new Place(entry.getKey(), null, entry.getValue())));
  }

  /**
   * @return the conflicts per element; the places of one element merge, and their ranges are lost
   */
  public @NotNull @Unmodifiable Map<@NotNull PsiElement, @NotNull Conflict> conflicts() {
    Map<PsiElement, Conflict> conflicts = new LinkedHashMap<>();
    for (Place place : places) {
      conflicts.merge(place.element(), place.conflict(), Conflict::merge);
    }
    return Collections.unmodifiableMap(conflicts);
  }

  /**
   * A conflict at a place.
   *
   * @param element        the element of the place
   * @param rangeInElement the place inside the element; null for the whole element
   * @param conflict       the conflict
   */
  public record Place(@NotNull PsiElement element, @Nullable TextRange rangeInElement, @NotNull Conflict conflict) {
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
    return places.isEmpty();
  }
}
