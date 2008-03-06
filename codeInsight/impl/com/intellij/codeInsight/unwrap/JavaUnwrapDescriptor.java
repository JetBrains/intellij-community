package com.intellij.codeInsight.unwrap;

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;

import java.util.ArrayList;
import java.util.List;

public class JavaUnwrapDescriptor implements UnwrapDescriptor {
  private static final Unwrapper[] UNWRAPPERS = new Unwrapper[] {
    new JavaElseUnwrapper(),
    new JavaElseRemover(),
    new JavaIfUnwrapper(),
    new JavaWhileUnwrapper(),
    new JavaForUnwrapper(),
    new JavaBracesUnwrapper(),
    new JavaTryUnwrapper(),
    new JavaCatchRemover(),
    new JavaSynchronizedUnwrapper(),
  };

  public List<Pair<PsiElement, Unwrapper>> collectUnwrappers(Project project, Editor editor, PsiFile file) {
    PsiElement e = findTargetElement(editor, file);

    List<Pair<PsiElement, Unwrapper>> result = new ArrayList<Pair<PsiElement, Unwrapper>>();
    while (e != null) {
      for (Unwrapper u : UNWRAPPERS) {
        if (u.isApplicableTo(e)) result.add(new Pair<PsiElement, Unwrapper>(e, u));
      }
      e = e.getParent();
    }

    return result;
  }

  public PsiElement findTargetElement(Editor editor, PsiFile file) {
    int offset = editor.getCaretModel().getOffset();
    return file.findElementAt(offset);
  }
}
