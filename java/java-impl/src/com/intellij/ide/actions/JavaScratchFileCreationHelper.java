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
public class JavaScratchFileCreationHelper extends ScratchFileCreationHelper {

  @Override
  public boolean prepareText(@NotNull Project project, @NotNull Context context, @NotNull DataContext dataContext) {
    String caretMarker = "CARET_MARKER";
    if (context.text == "") {
      String text = reformat(
        project, context.language,
        "class Scratch { public static void main (String[] args) {\n" + caretMarker + "\n} }");
      context.caretOffset = text.indexOf(caretMarker);
      context.text = text.substring(0, context.caretOffset) + text.substring(context.caretOffset + caretMarker.length());
      return true;
    }
    //todo add required import statements from dataContext if available
    PsiFile psi = parseHeader(project, context.language, context.text);
    SyntaxTraverser<PsiElement> s = SyntaxTraverser.psiTraverser();
    if (s.withRoot(psi).traverse().filter(PsiClass.class).first() != null ||
        s.withRoot(psi).traverse().filter(PsiErrorElement.class).first() == null) {
      return true;
    }
    psi = parseHeader(project, context.language, "class Scratch {\n" + context.text + "\n}");
    if (s.withRoot(psi).traverse().filter(PsiMethod.class).first() != null) {
      String text = reformat(
        project, context.language,
        "class Scratch {\n" + caretMarker + "\n}");
      context.caretOffset = text.indexOf(caretMarker);
      context.text = text.substring(0, context.caretOffset) +
                     StringUtil.trim(context.text) +
                     text.substring(context.caretOffset + caretMarker.length());
      return true;
    }

    psi = parseHeader(project, context.language, "class Scratch {\n" + context.text + "\n}");
    if (s.withRoot(psi).traverse().filter(PsiMember.class).first() != null) {
      String text = reformat(project, context.language,
                             "class Scratch { public static void main (String[] args) {\n" + caretMarker + "\n} }");
      context.caretOffset = text.indexOf(caretMarker);
      context.text = text.substring(0, context.caretOffset) +
                     StringUtil.trim(context.text) +
                     text.substring(context.caretOffset + caretMarker.length());
      return true;
    }
    return false;
  }
}
