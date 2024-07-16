// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.highlighting;

import com.intellij.ide.util.PsiClassListCellRenderer;
import com.intellij.ide.util.PsiElementListCellRenderer;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

abstract class ChooseClassAndDoHighlightRunnable extends ChooseOneOrAllRunnable<PsiClass> {
  ChooseClassAndDoHighlightRunnable(PsiClassType @NotNull [] classTypes, @NotNull Editor editor, @NotNull @NlsContexts.PopupTitle String title) {
    super(resolveClasses(classTypes), editor, title, PsiClass.class);
  }

  ChooseClassAndDoHighlightRunnable(@NotNull List<? extends PsiClass> classes, @NotNull Editor editor, @NotNull @NlsContexts.PopupTitle String title) {
    super(classes, editor, title, PsiClass.class);
  }

  public static @NotNull List<PsiClass> resolveClasses(PsiClassType @NotNull [] classTypes) {
    List<PsiClass> classes = new ArrayList<>();
    for (PsiClassType classType : classTypes) {
      PsiClass aClass = classType.resolve();
      if (aClass != null) classes.add(aClass);
    }
    return classes;
  }

  @Override
  protected @NotNull PsiElementListCellRenderer<PsiClass> createRenderer() {
    return new PsiClassListCellRenderer();
  }
}
