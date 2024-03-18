/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.ide.actions;

import com.intellij.ide.scratch.ScratchFileCreationHelper;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;

/**
 * @author gregsh
 */
public final class JavaScratchFileCreationHelper extends ScratchFileCreationHelper {

  @Override
  public boolean prepareText(@NotNull Project project, @NotNull Context context, @NotNull DataContext dataContext) {
    String caretMarker = "CARET_MARKER";
    if (context.text.isEmpty()) {
      String templateText = "class Scratch { public static void main (String[] args) {\n" + caretMarker + "\n} }";
      String text = reformat(project, context.language, templateText);
      context.caretOffset = text.indexOf(caretMarker);
      context.text = text.substring(0, context.caretOffset) + text.substring(context.caretOffset + caretMarker.length());
      return true;
    }
    //todo add required import statements from dataContext if available
    String textPrefix = StringUtil.trim(StringUtil.first(context.text, 1024, false));
    PsiFile psi = parseHeader(project, context.language, textPrefix);
    SyntaxTraverser<PsiElement> s = SyntaxTraverser.psiTraverser();
    for (PsiElement e : s.withRoot(psi)) {
      if (e instanceof PsiPackageStatement || e instanceof PsiImportStatement || e instanceof PsiClass) return true;
      if (e instanceof PsiErrorElement && e.getParent() == psi) break;
    }
    psi = parseHeader(project, context.language, "class Scratch {\n" + textPrefix + "\n}");
    PsiMember psiMember = s.withRoot(psi).traverse().filter(PsiMember.class).skip(1).first();
    String templateText =
      psiMember != null && psiMember.getText().contains(textPrefix)
      ? "class Scratch {\n" + caretMarker + "\n}"
      : "class Scratch { public static void main (String[] args) {\n" + caretMarker + "\n} }";
    String text = reformat(project, context.language, templateText);
    context.caretOffset = text.indexOf(caretMarker);
    context.text = text.substring(0, context.caretOffset) +
                 StringUtil.trim(context.text) +
                 text.substring(context.caretOffset + caretMarker.length());
    return true;
  }
}
