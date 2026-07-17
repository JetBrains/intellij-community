// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeHighlighting.TextEditorHighlightingPassRegistrar;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.UnfairTextRange;
import com.intellij.openapi.util.text.StringUtil;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMaps;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

final class FileStatus {
  static final TextRange WHOLE_FILE_TEXT_RANGE = new UnfairTextRange(-1, -1);
  private static final Logger LOG = Logger.getInstance(FileStatus.class);

  /**
   * The file was marked dirty without knowledge of specific dirty region. Subsequent markScopeDirty can refine dirty scope, not extend it
   */
  private boolean wolfPassFinished;

  /**
   * if contains the special value "WHOLE_FILE_MARKER" then the corresponding range is (0, document length)
   */
  private final Int2ObjectMap<RangeMarker> dirtyScopes = new Int2ObjectOpenHashMap<>();
  private final IntSet defensivelyMarked = new IntOpenHashSet();

  private boolean errorFound;

  FileStatus(@NotNull Project project) {
    markWholeFileDirty(project);
    LOG.debug("new FileStatus()");
  }

  static int @NotNull [] getAllKnownPassIds(@NotNull Project project) {
    return ((TextEditorHighlightingPassRegistrarImpl)TextEditorHighlightingPassRegistrar.getInstance(project)).getAllPassIds();
  }
  private void markWholeFileDirty(@NotNull Project project) {
    for (int passId : getAllKnownPassIds(project)) {
      setDirtyScope(passId, WholeFileDirtyMarker.INSTANCE);
    }
  }

  boolean isErrorFound() {
    return errorFound;
  }

  void setErrorFound(boolean errorFound) {
    this.errorFound = errorFound;
  }

  boolean isDefensivelyMarked(int passId) {
    return defensivelyMarked.contains(passId);
  }

  private void setDefensivelyMarked(int passId) {
    defensivelyMarked.add(passId);
  }
  void clearDefensivelyMarked(int passId) {
    defensivelyMarked.remove(passId);
  }
  void markDefensivelyForAllPasses(@NotNull Project project) {
    for (int passId : getAllKnownPassIds(project)) {
      setDefensivelyMarked(passId);
    }
  }
  void clearDefensivelyMarkedForAllPasses() {
    defensivelyMarked.clear();
  }
  boolean isDefensivelyMarkedForAnyPass() {
    return !defensivelyMarked.isEmpty();
  }

  boolean allDirtyScopesAreNull() {
    for (RangeMarker o : getAllDirtyScopes()) {
      if (o != null) return false;
    }
    return true;
  }

  // return true if myFileStatusMap has changed and we need restart
  boolean combineScopesWith(@NotNull TextRange newScope, @NotNull Document document) {
    boolean changed = false;
    for (ObjectIterator<Int2ObjectMap.Entry<RangeMarker>> it = Int2ObjectMaps.fastIterator(dirtyScopes); it.hasNext(); ) {
      Int2ObjectMap.Entry<RangeMarker> e = it.next();
      RangeMarker oldScope = e.getValue();
      RangeMarker newMarker = combineScopes(oldScope, newScope, document);
      changed |= !Objects.equals(oldScope, newMarker);
      e.setValue(newMarker);
    }
    return changed;
  }

  @Nullable RangeMarker getDirtyScope(int passId) {
    return dirtyScopes.get(passId);
  }

  void setWolfPassFinished() {
    this.wolfPassFinished = true;
  }

  boolean isWolfPassFinished() {
    return wolfPassFinished;
  }

  @NotNull Iterable<RangeMarker> getAllDirtyScopes() {
    return dirtyScopes.values();
  }

  boolean containsDirtyScope(int passId) {
    return dirtyScopes.containsKey(passId);
  }

  private static @NotNull RangeMarker combineScopes(@Nullable RangeMarker oldScope, @NotNull TextRange newScope, @NotNull Document document) {
    if (newScope == WHOLE_FILE_TEXT_RANGE || oldScope == WholeFileDirtyMarker.INSTANCE) {
      return WholeFileDirtyMarker.INSTANCE;
    }
    TextRange oldRange = oldScope == null ? null : oldScope.getTextRange();
    TextRange result = oldRange == null ? newScope : newScope.union(oldRange);
    if (oldScope != null && oldScope.isValid() && result.equals(oldRange)) {
      return oldScope;
    }
    if (result.getEndOffset() > document.getTextLength()) {
      if (result.getStartOffset() == 0) {
        return WholeFileDirtyMarker.INSTANCE;
      }
      result =  result.intersection(new TextRange(0, document.getTextLength()));
    }
    assert result != null;
    if (oldScope != null) {
      oldScope.dispose();
    }
    return document.createRangeMarker(result);
  }

  @Override
  public @NonNls String toString() {
    return
      (defensivelyMarked.isEmpty() ? "" : "defensivelyMarked = " + defensivelyMarked)
      +(wolfPassFinished ? "" : "; wolfPassFinished")
      +(errorFound ? "; errorFound" : "")
      +(dirtyScopes.isEmpty() ? "" : "; dirtyScopes: (" +
        (allDirtyScopesAreNull() ? "all null" :
                                     StringUtil.join(dirtyScopes.int2ObjectEntrySet(), e ->
        " pass: "+e.getIntKey()+" -> "+(e.getValue() == WholeFileDirtyMarker.INSTANCE ? "Whole file" : e.getValue()), ";")) + ")");
  }

  void setDirtyScope(int passId, @Nullable RangeMarker scope) {
    RangeMarker oldMarker = dirtyScopes.get(passId);
    if (oldMarker != scope) {
      if (oldMarker != null) {
        oldMarker.dispose();
      }
      if (LOG.isDebugEnabled()) {
        LOG.debug("FileStatus.setDirtyScope("+passId+", "+scope+")");
      }
      dirtyScopes.put(passId, scope);
    }
  }
}
