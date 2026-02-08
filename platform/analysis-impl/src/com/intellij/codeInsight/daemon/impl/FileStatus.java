// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeHighlighting.DirtyScopeTrackingHighlightingPassFactory;
import com.intellij.codeHighlighting.Pass;
import com.intellij.codeHighlighting.TextEditorHighlightingPassRegistrar;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.UnfairTextRange;
import com.intellij.openapi.util.text.StringUtil;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
  }

  int @NotNull [] getAllKnownPassIds(@NotNull Project project) {
    IntList r = IntArrayList.of(Pass.UPDATE_ALL, Pass.EXTERNAL_TOOLS, Pass.LOCAL_INSPECTIONS, Pass.LINE_MARKERS, Pass.SLOW_LINE_MARKERS, Pass.INJECTED_GENERAL);
    TextEditorHighlightingPassRegistrarEx registrar = (TextEditorHighlightingPassRegistrarEx)TextEditorHighlightingPassRegistrar.getInstance(project);
    for (DirtyScopeTrackingHighlightingPassFactory factory : registrar.getDirtyScopeTrackingFactories()) {
      r.add(factory.getPassId());
    }
    return r.toIntArray();
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

  void setDefensivelyMarked(boolean defensivelyMarked, int passId) {
    if (defensivelyMarked) {
      this.defensivelyMarked.add(passId);
    }
    else {
      this.defensivelyMarked.remove(passId);
    }
  }
  void markDefensivelyMarkedForAllPasses(@NotNull Project project) {
    for (int passId : getAllKnownPassIds(project)) {
      setDefensivelyMarked(true, passId);
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

  void combineScopesWith(@NotNull TextRange scope, @NotNull Document document) {
    dirtyScopes.replaceAll((__, oldScope) -> combineScopes(oldScope, scope, document));
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
    RangeMarker marker = dirtyScopes.get(passId);
    if (marker != scope) {
      if (marker != null) {
        marker.dispose();
      }
      if (LOG.isDebugEnabled()) {
        LOG.debug("FileStatus.setDirtyScope("+passId+", "+scope+")");
      }
      dirtyScopes.put(passId, scope);
    }
  }
}
