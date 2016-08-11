/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.util.ChangeToAppendUtil;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

/**
 * @author Bas Leijdekkers
 */
public class ChangeToAppendFix implements IntentionAction {

  private final IElementType myTokenType;
  private final PsiType myLhsType;
  private final PsiAssignmentExpression myAssignmentExpression;
  private volatile TypeInfo myTypeInfo;

  public ChangeToAppendFix(@NotNull IElementType eqOpSign, @NotNull PsiType lType, @NotNull PsiAssignmentExpression assignmentExpression) {
    myTokenType = eqOpSign;
    myLhsType = lType;
    myAssignmentExpression = assignmentExpression;
  }

  @NotNull
  @Override
  public String getText() {
    return QuickFixBundle.message("change.to.append.text",
                                  ChangeToAppendUtil.buildAppendExpression(myAssignmentExpression.getRExpression(),
                                                                           getTypeInfo().myUseStringValueOf,
                                                                           new StringBuilder(myAssignmentExpression.getLExpression().getText())));
  }

  @NotNull
  @Override
  public String getFamilyName() {
    return QuickFixBundle.message("change.to.append.family");
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    return JavaTokenType.PLUSEQ == myTokenType &&
           myAssignmentExpression.isValid() &&
           PsiManager.getInstance(project).isInProject(myAssignmentExpression) &&
           getTypeInfo().myAppendable;
  }

  @Override
  public boolean startInWriteAction() {
    return true;
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    if (!FileModificationService.getInstance().prepareFileForWrite(file)) return;
    final PsiExpression appendExpression =
      ChangeToAppendUtil.buildAppendExpression(myAssignmentExpression.getLExpression(), myAssignmentExpression.getRExpression());
    if (appendExpression == null) return;
    myAssignmentExpression.replace(appendExpression);
  }

  @NotNull
  private TypeInfo getTypeInfo() {
    if (myTypeInfo != null) return myTypeInfo;
    myTypeInfo = calculateTypeInfo();
    return myTypeInfo;
  }

  @NotNull
  private TypeInfo calculateTypeInfo() {
    if (myLhsType.equalsToText(CommonClassNames.JAVA_LANG_STRING_BUILDER) ||
        myLhsType.equalsToText(CommonClassNames.JAVA_LANG_STRING_BUFFER)) {
      return new TypeInfo(true, false);
    }
    if (InheritanceUtil.isInheritor(myLhsType, "java.lang.Appendable")) {
      return new TypeInfo(true, true);
    }
    return new TypeInfo(false, false);
  }

  private static class TypeInfo {
    private final boolean myAppendable;
    private final boolean myUseStringValueOf;

    TypeInfo(boolean appendable, boolean useStringValueOf) {
      myAppendable = appendable;
      myUseStringValueOf = useStringValueOf;
    }
  }
}
