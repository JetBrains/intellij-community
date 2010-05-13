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

package com.intellij.lang.java;

import com.intellij.lang.ImportOptimizer;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.EmptyRunnable;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiImportList;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

/**
 * @author max
 */
public class JavaImportOptimizer implements ImportOptimizer {
  private static final Logger LOG = Logger.getInstance("#com.intellij.lang.java.JavaImportOptimizer");

  @NotNull
  public Runnable processFile(final PsiFile file) {
    if (!(file instanceof PsiJavaFile)) {
      return EmptyRunnable.getInstance();
    }
    Project project = file.getProject();
    final PsiImportList newImportList = JavaCodeStyleManager.getInstance(project).prepareOptimizeImportsResult((PsiJavaFile)file);
    if (newImportList == null) return EmptyRunnable.getInstance();
    return new Runnable() {
      public void run() {
        try {
          final PsiDocumentManager manager = PsiDocumentManager.getInstance(file.getProject());
          final Document document = manager.getDocument(file);
          if (document != null) {
            manager.commitDocument(document);
          }
          final PsiImportList oldImportList = ((PsiJavaFile)file).getImportList();
          assert oldImportList != null;
          oldImportList.replace(newImportList);
        }
        catch (IncorrectOperationException e) {
          LOG.error(e);
        }
      }
    };
  }

  public boolean supports(PsiFile file) {
    return file instanceof PsiJavaFile;
  }
}
