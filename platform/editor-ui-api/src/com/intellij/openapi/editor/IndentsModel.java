/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

/*
 * @author max
 */
package com.intellij.openapi.editor;

import org.jetbrains.annotations.Nullable;

import java.util.List;

public interface IndentsModel {
  @Nullable
  IndentGuideDescriptor getCaretIndentGuide();

  /**
   * Tries to return a descriptor (if any) that defines indent guide for the given lines.
   *
   * @param startLine   logical line where target indent guide is started
   * @param endLine     logical line where target indent guide is ended
   * @return            indent guide descriptor registered for the given lines at the current model previously if any;
   *                    {@code null} otherwise
   */
  @Nullable
  IndentGuideDescriptor getDescriptor(int startLine, int endLine);

  void assumeIndents(List<IndentGuideDescriptor> descriptors);

}
