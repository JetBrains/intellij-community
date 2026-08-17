// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor;

import org.jetbrains.annotations.NotNull;

/**
 * This object is used to store/restore editor state between restarts.
 * For example, text editor can store caret position, scroll position,
 * information about folded regions, etc.
 * <p>
 * Undo subsystem expects a sensible implementation of {@link Object#equals(Object)} method of state instances.
 * In particular, {@code state1} and {@code state2} in the following situation
 * <pre>{@code
 *   FileEditorState state1 = fileEditor.getState(FileEditorStateLevel.UNDO);
 *   ...
 *   fileEditor.setState(state1);
 *   FileEditorState state2 = fileEditor.getState(FileEditorStateLevel.UNDO);
 * }</pre>
 * are expected to be 'equal'.
 */
@FunctionalInterface
public interface FileEditorState {
  FileEditorState INSTANCE = (_, _) -> true;

  /**
   * A sentinel returned by {@code FileEditorProvider.readState} implementations (in particular by the default ones)
   * when there is nothing to restore &mdash; e.g. the provider does not implement deserialization, or the backing
   * {@link com.intellij.openapi.vfs.VirtualFile} cannot be resolved.
   * <p>
   * Unlike {@link #INSTANCE}, which is a trivial-but-valid state, this value must be treated by callers as
   * "no state at all": it must not be stored in the editor history, applied via {@link FileEditor#setState},
   * or handed to {@code FileEditorProvider.writeState} (doing so may fail, since {@code writeState} implementations
   * typically cast the state to their own type).
   */
  FileEditorState NO_STATE = (_, _) -> true;

  boolean canBeMergedWith(@NotNull FileEditorState otherState, @NotNull FileEditorStateLevel level);
}
