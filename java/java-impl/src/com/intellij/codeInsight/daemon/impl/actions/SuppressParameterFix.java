/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.codeInsight.daemon.impl.actions;

import com.intellij.codeInsight.CodeInsightUtilBase;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.SuppressIntentionAction;
import com.intellij.codeInspection.SuppressManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.impl.storage.ClasspathStorage;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

/**
 * @author ven
 */
public class SuppressParameterFix extends SuppressIntentionAction {
  private String myID;
  private String myAlternativeID;

  public SuppressParameterFix(HighlightDisplayKey key) {
    this(key.getID());
    myAlternativeID = HighlightDisplayKey.getAlternativeID(key);
  }

  public SuppressParameterFix(String ID) {
    myID = ID;
  }

  @NotNull
  public String getText() {
    return "Suppress for parameter";
  }

  @NotNull
  public String getFamilyName() {
    return InspectionsBundle.message("suppress.inspection.family");
  }

  public boolean isAvailable(@NotNull final Project project, final Editor editor, @NotNull final PsiElement context) {
    PsiParameter psiParameter = PsiTreeUtil.getParentOfType(context, PsiParameter.class, false);
    return psiParameter != null && SuppressManager.getInstance().canHave15Suppressions(psiParameter);
  }

  public void invoke(final Project project, final Editor editor, final PsiElement element) throws IncorrectOperationException {
    PsiParameter container = PsiTreeUtil.getParentOfType(element, PsiParameter.class, false);
    assert container != null;
    if (!CodeInsightUtilBase.preparePsiElementForWrite(container)) return;
    final PsiModifierList modifierList = container.getModifierList();
    if (modifierList != null) {
      final String id = SuppressFix.getID(container, myAlternativeID);
      SuppressFix.addSuppressAnnotation(project, editor, container, container, id != null ? id : myID);
    }
    DaemonCodeAnalyzer.getInstance(project).restart();
  }
}
