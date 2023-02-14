/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.diff;

import java.util.List;

/**
 * Allows removing other DiffTools from the list of available if current one can show request.
 * <p>
 * 4ex: this could be used by 'image comparator plugin' to hide default binary diff tool
 * or by 'code review plugin' to silently replace default SimpleDiffTool with the one that supports review comments.
 *
 * @see com.intellij.diff.tools.simple.SimpleDiffTool
 * @see com.intellij.diff.tools.fragmented.UnifiedDiffTool
 * @see com.intellij.diff.tools.binary.BinaryDiffTool
 * @see com.intellij.openapi.vcs.changes.actions.diff.lst.LocalChangeListDiffTool
 */
public interface SuppressiveDiffTool extends DiffTool {
  List<Class<? extends DiffTool>> getSuppressedTools();
}
