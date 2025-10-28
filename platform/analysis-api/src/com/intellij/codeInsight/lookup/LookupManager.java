// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.lookup;

import com.intellij.codeWithMe.ClientId;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.editor.ClientEditorManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageEditorUtil;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.beans.PropertyChangeListener;

/**
 * Manages active lookups.
 *
 * @see Lookup
 */
public abstract class LookupManager {
  public static LookupManager getInstance(@NotNull Project project) {
    return project.getService(LookupManager.class);
  }

  public static @Nullable LookupEx getActiveLookup(@Nullable Editor editor) {
    if (editor == null) return null;

    final Project project = editor.getProject();
    if (project == null || project.isDisposed()) return null;

    try (AccessToken ignored = ClientId.withClientId(ClientEditorManager.getClientId(editor))) {
      final LookupEx lookup = getInstance(project).getActiveLookup();
      if (lookup == null) return null;

      return lookup.getTopLevelEditor() == InjectedLanguageEditorUtil.getTopLevelEditor(editor) ? lookup : null;
    }
  }

  public @Nullable LookupEx showLookup(@NotNull Editor editor, LookupElement @NotNull ... items) {
    return showLookup(editor, items, "", new LookupArranger.DefaultArranger());
  }

  public @Nullable LookupEx showLookup(@NotNull Editor editor, LookupElement @NotNull [] items, @NotNull String prefix) {
    return showLookup(editor, items, prefix, new LookupArranger.DefaultArranger());
  }

  /**
   * Creates and shows a lookup with the specified items.
   */
  @RequiresEdt
  public abstract @Nullable LookupEx showLookup(@NotNull Editor editor,
                                                LookupElement @NotNull [] items,
                                                @NotNull String prefix,
                                                @NotNull LookupArranger arranger);

  public abstract void hideActiveLookup();

  public static void hideActiveLookup(@NotNull Project project) {
    LookupManager lookupManager = project.getServiceIfCreated(LookupManager.class);
    if (lookupManager != null) {
      lookupManager.hideActiveLookup();
    }
  }

  public abstract @Nullable LookupEx getActiveLookup();

  public static final String PROP_ACTIVE_LOOKUP = "activeLookup";

  /** @deprecated Use {@link LookupManagerListener#TOPIC} */
  @Deprecated
  public abstract void addPropertyChangeListener(@NotNull PropertyChangeListener listener);

  /** @deprecated Use {@link LookupManagerListener#TOPIC} */
  @Deprecated(forRemoval = true)
  public abstract void addPropertyChangeListener(@NotNull PropertyChangeListener listener, @NotNull Disposable disposable);

  /** @deprecated Use {@link LookupManagerListener#TOPIC} */
  @Deprecated(forRemoval = true)
  public abstract void removePropertyChangeListener(@NotNull PropertyChangeListener listener);

  /**
   * Creates a lookup with the specified items. Does not show it.
   */
  @RequiresEdt
  public abstract @NotNull Lookup createLookup(@NotNull Editor editor,
                                               @NotNull LookupElement @NotNull [] items,
                                               @NotNull String prefix,
                                               @NotNull LookupArranger arranger);
}
