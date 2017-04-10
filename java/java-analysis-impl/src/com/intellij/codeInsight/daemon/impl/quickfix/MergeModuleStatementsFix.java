/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Iterator;
import java.util.List;
import java.util.StringJoiner;

/**
 * @author Pavel.Dolgov
 */
public abstract class MergeModuleStatementsFix<T extends PsiElement> extends LocalQuickFixAndIntentionActionOnPsiElement {
  protected final SmartPsiElementPointer<T> myOtherStatement;

  protected MergeModuleStatementsFix(@NotNull T thisStatement, @NotNull T otherStatement) {
    super(thisStatement);
    final PsiFile file = otherStatement.getContainingFile();
    myOtherStatement = SmartPointerManager.getInstance(otherStatement.getProject()).createSmartPsiElementPointer(otherStatement, file);
  }

  @Override
  public boolean isAvailable(@NotNull Project project,
                             @NotNull PsiFile file,
                             @NotNull PsiElement startElement,
                             @NotNull PsiElement endElement) {
    final T otherStatement = myOtherStatement.getElement();
    return otherStatement != null && otherStatement.isValid() && PsiUtil.isLanguageLevel9OrHigher(file);
  }

  @Override
  public void invoke(@NotNull Project project,
                     @NotNull PsiFile file,
                     @Nullable Editor editor,
                     @NotNull PsiElement thisStatement,
                     @NotNull PsiElement endElement) {
    final T otherStatement = myOtherStatement.getElement();

    if (otherStatement != null) {
      final PsiElement parent = otherStatement.getParent();
      if (parent instanceof PsiJavaModule) {
        final String moduleName = ((PsiJavaModule)parent).getName();
        final String moduleText = PsiKeyword.MODULE + " " + moduleName + " {" + getReplacementText(otherStatement) + "}";
        final PsiElementFactory factory = JavaPsiFacade.getInstance(project).getElementFactory();
        final PsiJavaModule tempModule = factory.createModuleFromText(moduleText);

        final Iterator<T> statementIterator = getStatements(tempModule).iterator();
        LOG.assertTrue(statementIterator.hasNext());
        final T replacement = statementIterator.next();

        final CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(project);
        codeStyleManager.reformat(otherStatement.replace(replacement));
        thisStatement.delete();
      }
    }
  }

  @NotNull
  protected abstract String getReplacementText(@NotNull T otherStatement);

  @NotNull
  protected abstract Iterable<T> getStatements(@NotNull PsiJavaModule javaModule);

  @NotNull
  protected static String joinNames(@NotNull List<String> oldNames, @NotNull List<String> newNames) {
    final StringJoiner joiner = new StringJoiner(",");
    oldNames.forEach(joiner::add);
    newNames.stream().filter(name -> !oldNames.contains(name)).forEach(joiner::add);
    return joiner.toString();
  }

  @Nullable
  public static MergeModuleStatementsFix createFix(@Nullable PsiElement statement) {
    if (statement instanceof PsiPackageAccessibilityStatement) {
      return MergePackageAccessibilityStatementsFix.createFix((PsiPackageAccessibilityStatement)statement);
    }
    else if (statement instanceof PsiProvidesStatement) {
      return MergeProvidesStatementsFix.createFix((PsiProvidesStatement)statement);
    }
    return null;
  }
}
