/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.openapi.fileTypes;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

public abstract class SingleLazyInstanceSyntaxHighlighterFactory extends SyntaxHighlighterFactory {
  private SyntaxHighlighter myValue;

  @NotNull
  public final SyntaxHighlighter getSyntaxHighlighter(final Project project, final VirtualFile virtualFile) {
    if (myValue == null) {
      myValue = createHighlighter();
    }
    return myValue;
  }

  protected abstract @NotNull SyntaxHighlighter createHighlighter();
}