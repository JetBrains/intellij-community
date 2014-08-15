/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.intellij.lang.regexp.intention;

import com.intellij.codeInsight.intention.impl.QuickEditAction;
import com.intellij.lang.Language;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.intellij.lang.regexp.RegExpLanguage;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author Konstantin Bulenkov
 * @author Anna Bulenkova
 */
public class CheckRegExpIntentionAction extends QuickEditAction implements Iconable {

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    Pair<PsiElement, TextRange> pair = getRangePair(file, editor);
    if (pair != null && pair.first != null) {
      Language language = pair.first.getLanguage();
      return language.isKindOf(RegExpLanguage.INSTANCE);
    }
    return false;
  }

  @Override
  protected boolean isShowInBalloon() {
    return true;
  }

  @Override
  protected JComponent createBalloonComponent(@NotNull PsiFile file) {
    final Project project = file.getProject();
    final Document document = PsiDocumentManager.getInstance(project).getDocument(file);
    if (document != null) {
      return new CheckRegExpForm(file).getRootPanel();
    }
    return null;
  }

  @NotNull
  @Override
  public String getText() {
    return "Check RegExp";
  }

  @NotNull
  @Override
  public String getFamilyName() {
    return getText();
  }

  @Override
  public Icon getIcon(int flags) {
    //noinspection ConstantConditions
    return RegExpLanguage.INSTANCE.getAssociatedFileType().getIcon();
  }
}
