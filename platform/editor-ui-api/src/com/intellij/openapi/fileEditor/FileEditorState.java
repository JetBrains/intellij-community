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

/**
 * This object is used to store/restore editor state between restarts.
 * For example, text editor can store caret position, scroll position,
 * information about folded regions, etc.
 *
 * @author Vladimir Kondratyev
 */
@FunctionalInterface
public interface FileEditorState {
  FileEditorState INSTANCE = (__0, __1) -> true;

  boolean canBeMergedWith(FileEditorState otherState, FileEditorStateLevel level);
}
