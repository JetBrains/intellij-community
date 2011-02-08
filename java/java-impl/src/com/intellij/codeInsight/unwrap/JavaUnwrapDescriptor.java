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
package com.intellij.codeInsight.unwrap;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class JavaUnwrapDescriptor implements UnwrapDescriptor {
  private static final Unwrapper[] UNWRAPPERS = new Unwrapper[] {
    new JavaMethodParameterUnwrapper(),
    new JavaElseUnwrapper(),
    new JavaElseRemover(),
    new JavaIfUnwrapper(),
    new JavaWhileUnwrapper(),
    new JavaForUnwrapper(),
    new JavaBracesUnwrapper(),
    new JavaTryUnwrapper(),
    new JavaCatchRemover(),
    new JavaSynchronizedUnwrapper(),
    new JavaAnonymousUnwrapper(),
  };

  public List<Pair<PsiElement, Unwrapper>> collectUnwrappers(Project project, Editor editor, PsiFile file) {
    PsiElement e = findTargetElement(editor, file);

    List<Pair<PsiElement, Unwrapper>> result = new ArrayList<Pair<PsiElement, Unwrapper>>();
    Set<PsiElement> ignored = new HashSet<PsiElement>();
    while (e != null) {
      for (Unwrapper u : UNWRAPPERS) {
        if (u.isApplicableTo(e) && !ignored.contains(e)) {
          result.add(new Pair<PsiElement, Unwrapper>(e, u));
          u.collectElementsToIgnore(e, ignored);
        }
      }
      e = e.getParent();
    }

    return result;
  }

  @Nullable
  public static PsiElement findTargetElement(Editor editor, PsiFile file) {
    int offset = editor.getCaretModel().getOffset();
    PsiElement endElement = file.findElementAt(offset);
    SelectionModel selectionModel = editor.getSelectionModel();
    if (selectionModel.hasSelection() && selectionModel.getSelectionStart() < offset) {
      PsiElement startElement = file.findElementAt(selectionModel.getSelectionStart());
      if (startElement != null && startElement != endElement && startElement.getTextRange().getEndOffset() == offset) {
        return startElement;
      }
    }
    return endElement;
  }

  public boolean showOptionsDialog() {
    return true;
  }

  public boolean shouldTryToRestoreCaretPosition() {
    return true;
  }
}
