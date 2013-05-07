/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
 * @author Evgeny Gerashchenko
 */
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.actions.CodeInsightAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class NextPrevParameterAction extends CodeInsightAction {
  private final boolean myNext;

  protected NextPrevParameterAction(boolean next) {
    myNext = next;
  }

  @NotNull
  @Override
  public CodeInsightActionHandler getHandler() {
    return new Handler();
  }

  @Override
  protected boolean isValidForFile(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file) {
    return hasSutablePolicy(editor, file);
  }

  public static boolean hasSutablePolicy(Editor editor, PsiFile file) {
    return findSuitableTraversalPolicy(editor, file) != null;
  }

  @Nullable
  private static TemplateParameterTraversalPolicy findSuitableTraversalPolicy(Editor editor, PsiFile file) {
    for (TemplateParameterTraversalPolicy policy : Extensions.getExtensions(TemplateParameterTraversalPolicy.EP_NAME)) {
      if (policy.isValidForFile(editor, file)) {
        return policy;
      }
    }
    return null;
  }

  private class Handler implements CodeInsightActionHandler {
    @Override
    public void invoke(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file) {
      TemplateParameterTraversalPolicy policy = findSuitableTraversalPolicy(editor, file);
      if (policy != null) {
        PsiDocumentManager.getInstance(project).commitAllDocuments();

        policy.invoke(editor, file, myNext);
      }
    }

    @Override
    public boolean startInWriteAction() {
      return false;
    }
  }

  public static class Next extends NextPrevParameterAction {
    public Next() {
      super(true);
    }
  }

  public static class Prev extends NextPrevParameterAction {
    public Prev() {
      super(false);
    }
  }
}
