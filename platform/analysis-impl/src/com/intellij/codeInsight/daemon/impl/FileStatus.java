// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeHighlighting.DirtyScopeTrackingHighlightingPassFactory;
import com.intellij.codeHighlighting.Pass;
import com.intellij.codeHighlighting.TextEditorHighlightingPassRegistrar;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.UnfairTextRange;
import com.intellij.openapi.util.text.StringUtil;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class FileStatus {
  static final TextRange WHOLE_FILE_TEXT_RANGE = new UnfairTextRange(-1, -1);

  private boolean defensivelyMarked;

  /**
   * The file was marked dirty without knowledge of specific dirty region. Subsequent markScopeDirty can refine dirty scope, not extend it
   */
  private boolean wolfPassFinished;

  /**
   * if contains the special value "WHOLE_FILE_MARKER" then the corresponding range is (0, document length)
   */
  private final Int2ObjectMap<RangeMarker> dirtyScopes = new Int2ObjectOpenHashMap<>(); // guarded by myDocumentToStatusMap

  private boolean errorFound;

  FileStatus(@NotNull Project project) {
    markWholeFileDirty(project);
  }

  void markWholeFileDirty(@NotNull Project project) {
    setDirtyScope(Pass.UPDATE_ALL, WholeFileDirtyMarker.INSTANCE);
    setDirtyScope(Pass.EXTERNAL_TOOLS, WholeFileDirtyMarker.INSTANCE);
    setDirtyScope(Pass.LOCAL_INSPECTIONS, WholeFileDirtyMarker.INSTANCE);
    setDirtyScope(Pass.LINE_MARKERS, WholeFileDirtyMarker.INSTANCE);
    setDirtyScope(Pass.SLOW_LINE_MARKERS, WholeFileDirtyMarker.INSTANCE);
    setDirtyScope(Pass.INJECTED_GENERAL, WholeFileDirtyMarker.INSTANCE);

    var registrar = (TextEditorHighlightingPassRegistrarEx)TextEditorHighlightingPassRegistrar.getInstance(project);
    for (DirtyScopeTrackingHighlightingPassFactory factory : registrar.getDirtyScopeTrackingFactories()) {
      setDirtyScope(factory.getPassId(), WholeFileDirtyMarker.INSTANCE);
    }
  }

  boolean isErrorFound() {
    return errorFound;
  }

  void setErrorFound(boolean errorFound) {
    this.errorFound = errorFound;
  }

  boolean isDefensivelyMarked() {
    return defensivelyMarked;
  }

  void setDefensivelyMarked(boolean defensivelyMarked) {
    this.defensivelyMarked = defensivelyMarked;
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

  Iterable<RangeMarker> getAllDirtyScopes() {
    return dirtyScopes.values();
  }

  boolean containsDirtyScope(int passId) {
    return dirtyScopes.containsKey(passId);
  }

  static @NotNull RangeMarker combineScopes(@Nullable RangeMarker old, @NotNull TextRange scope, @NotNull Document document) {
    if (scope == WHOLE_FILE_TEXT_RANGE || old == WholeFileDirtyMarker.INSTANCE) {
      return WholeFileDirtyMarker.INSTANCE;
    }
    TextRange oldRange = old == null ? null : old.getTextRange();
    TextRange result = oldRange == null ? scope : scope.union(oldRange);
    if (old != null && old.isValid() && result.equals(oldRange)) {
      return old;
    }
    if (result.getEndOffset() > document.getTextLength()) {
      if (result.getStartOffset() == 0) {
        return WholeFileDirtyMarker.INSTANCE;
      }
      result =  result.intersection(new TextRange(0, document.getTextLength()));
    }
    assert result != null;
    if (old != null) {
      old.dispose();
    }
    return document.createRangeMarker(result);
  }

  @Override
  public @NonNls String toString() {
    return
      (defensivelyMarked ? "defensivelyMarked = "+defensivelyMarked : "")
      +(wolfPassFinished ? "" : "; wolfPassFinished = "+wolfPassFinished)
      +(errorFound ? "; errorFound = "+errorFound : "")
      +(dirtyScopes.isEmpty() ? "" : "; dirtyScopes: (" +
                                     StringUtil.join(dirtyScopes.int2ObjectEntrySet(), e ->
        " pass: "+e.getIntKey()+" -> "+(e.getValue() == WholeFileDirtyMarker.INSTANCE ? "Whole file" : e.getValue()), ";") + ")");
  }

  void setDirtyScope(int passId, @Nullable RangeMarker scope) {
    RangeMarker marker = dirtyScopes.get(passId);
    if (marker != scope) {
      if (marker != null) {
        marker.dispose();
      }
      dirtyScopes.put(passId, scope);
    }
  }
}
