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
package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.daemon.impl.analysis.JavaHighlightUtil;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.QuickFixFactory;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * @author cdr
 */
public class MoveInitializerToConstructorAction extends BaseMoveInitializerToMethodAction {
  @Override
  @NotNull
  public String getFamilyName() {
    return getText();
  }

  @Override
  @NotNull
  public String getText() {
    return CodeInsightBundle.message("intention.move.initializer.to.constructor");
  }

  @NotNull
  @Override
  protected Collection<String> getUnsuitableModifiers() {
    return Arrays.asList(PsiModifier.STATIC);
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
  private static Collection<PsiMethod> removeChainedConstructors(@NotNull Collection<PsiMethod> constructors) {
    final List<PsiMethod> result = new ArrayList<>(constructors);
    for (Iterator<PsiMethod> iterator = result.iterator(); iterator.hasNext(); ) {
      final PsiMethod constructor = iterator.next();
      if (JavaHighlightUtil.getChainedConstructors(constructor) != null) {
        iterator.remove();
      }
    }
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