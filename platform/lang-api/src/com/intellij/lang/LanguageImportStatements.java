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

/*
 * @author max
 */
package com.intellij.lang;

import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class LanguageImportStatements extends LanguageExtension<ImportOptimizer> {
  public static final LanguageImportStatements INSTANCE = new LanguageImportStatements();

  private LanguageImportStatements() {
    super("com.intellij.lang.importOptimizer");
  }

  @NotNull
  public Set<ImportOptimizer> forFile(@NotNull PsiFile file) {
    Set<ImportOptimizer> optimizers = new HashSet<>();
    for (PsiFile psiFile : file.getViewProvider().getAllFiles()) {
      List<ImportOptimizer> langOptimizers = allForLanguage(psiFile.getLanguage());
      for (ImportOptimizer optimizer : langOptimizers) {
        if (optimizer != null && optimizer.supports(psiFile)) {
          optimizers.add(optimizer);
          break;
        }
      }
    }
    return optimizers;
  }
}