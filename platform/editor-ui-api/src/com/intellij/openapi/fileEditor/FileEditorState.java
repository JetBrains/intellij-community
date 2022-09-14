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
  FileEditorState INSTANCE = (__0, __1) -> true;

  boolean canBeMergedWith(@NotNull FileEditorState otherState, @NotNull FileEditorStateLevel level);
}
