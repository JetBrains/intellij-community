// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.daemon.impl.analysis.JavaHighlightUtil;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.QuickFixFactory;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class MoveInitializerToConstructorAction extends BaseMoveInitializerToMethodAction {
  @Override
  @NotNull
  public String getFamilyName() {
    return getText();
  }

  @Override
  @NotNull
  public String getText() {
    return JavaBundle.message("intention.move.initializer.to.constructor");
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, @NotNull PsiElement element) {
    if (super.isAvailable(project, editor, element)) {
      final PsiField field = PsiTreeUtil.getParentOfType(element, PsiField.class);
      assert field != null;
      if (field.hasModifierProperty(PsiModifier.FINAL)) {
        PsiClass containingClass = field.getContainingClass();
        assert containingClass != null;
        PsiClassInitializer[] initializers = containingClass.getInitializers();
        PsiElement[] elements =
          Arrays.stream(containingClass.getFields())
          .map(f -> f.getInitializer())
          .filter(Objects::nonNull)
          .toArray(PsiElement[]::new);
        return ReferencesSearch.search(field, new LocalSearchScope(ArrayUtil.mergeArrays(elements, initializers))).findFirst() == null;
      }
      return true;
    }
    return false;
  }

  @NotNull
  @Override
  protected Collection<String> getUnsuitableModifiers() {
    return Collections.singletonList(PsiModifier.STATIC);
  }

  @NotNull
  @Override
  protected Collection<PsiMethod> getOrCreateMethods(@NotNull Project project, @NotNull Editor editor, PsiFile file, @NotNull PsiClass aClass) {
    final Collection<PsiMethod> constructors = Arrays.asList(aClass.getConstructors());
    if (constructors.isEmpty()) {
      return createConstructor(project, editor, file, aClass);
    }

    return removeChainedConstructors(constructors);
  }

  @NotNull
  private static Collection<PsiMethod> removeChainedConstructors(@NotNull Collection<? extends PsiMethod> constructors) {
    final List<PsiMethod> result = new ArrayList<>(constructors);
    result.removeIf(constructor -> !JavaHighlightUtil.getChainedConstructors(constructor).isEmpty());
    return result;
  }

  @NotNull
  private static Collection<PsiMethod> createConstructor(@NotNull Project project,
                                                         @NotNull Editor editor,
                                                         PsiFile file,
                                                         @NotNull PsiClass aClass) {
    final IntentionAction addDefaultConstructorFix = QuickFixFactory.getInstance().createAddDefaultConstructorFix(aClass);
    final int offset = editor.getCaretModel().getOffset();
    addDefaultConstructorFix.invoke(project, editor, file);
    editor.getCaretModel().moveToOffset(offset); //restore caret
    return Arrays.asList(aClass.getConstructors());
  }
}