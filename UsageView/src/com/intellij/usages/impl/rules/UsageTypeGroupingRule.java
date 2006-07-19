/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
package com.intellij.usages.impl.rules;

import com.intellij.openapi.vcs.FileStatus;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.usages.Usage;
import com.intellij.usages.UsageGroup;
import com.intellij.usages.UsageView;
import com.intellij.usages.rules.PsiElementUsage;
import com.intellij.usages.rules.UsageGroupingRule;

import javax.swing.*;

/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Dec 17, 2004                                                                        m n m
 * Time: 9:34:53 PM
 * To change this template use File | Settings | File Templates.
 */
public class UsageTypeGroupingRule implements UsageGroupingRule {
  public UsageGroup groupUsage(Usage usage) {
    if (usage instanceof PsiElementUsage) {
      PsiElementUsage elementUsage = (PsiElementUsage)usage;

      UsageType usageType = getUsageType(elementUsage.getElement());
      if (usageType == null) return null;

      return new UsageTypeGroup(usageType);
    }
    return null;
  }

  private UsageType getUsageType(PsiElement element) {
    if (element == null) return null;

    UsageType classUsageType = getClassUsageType(element);
    if (classUsageType != null) return classUsageType;

    if (element instanceof PsiLiteralExpression) {return UsageType.LITERAL_USAGE; }

    if (PsiTreeUtil.getParentOfType(element, PsiComment.class, false) != null) { return UsageType.COMMENT_USAGE; }

    return UsageType.UNCLASSIFIED;
  }

  private UsageType getClassUsageType(PsiElement element) {

    if (PsiTreeUtil.getParentOfType(element, PsiImportStatement.class, false) != null) return UsageType.CLASS_IMPORT;
    PsiReferenceList referenceList = PsiTreeUtil.getParentOfType(element, PsiReferenceList.class);
    if (referenceList != null) {
      if (referenceList.getParent() instanceof PsiClass) return UsageType.CLASS_EXTENDS_IMPLEMENTS_LIST;
      if (referenceList.getParent() instanceof PsiMethod) return UsageType.CLASS_METHOD_THROWS_LIST;
    }

    PsiTypeCastExpression castExpression = PsiTreeUtil.getParentOfType(element, PsiTypeCastExpression.class);
    if (castExpression != null) {
      if (PsiTreeUtil.isAncestor(castExpression.getCastType(), element, true)) return UsageType.CLASS_CAST_TO;
    }

    PsiInstanceOfExpression instanceOfExpression = PsiTreeUtil.getParentOfType(element, PsiInstanceOfExpression.class);
    if (instanceOfExpression != null) {
      if (PsiTreeUtil.isAncestor(instanceOfExpression.getCheckType(), element, true)) return UsageType.CLASS_INSTANCE_OF;
    }

    if (PsiTreeUtil.getParentOfType(element, PsiClassObjectAccessExpression.class) != null) return UsageType.CLASS_CLASS_OBJECT_ACCESS;

    if (element instanceof PsiReferenceExpression) {
      PsiReferenceExpression expression = (PsiReferenceExpression)element;
      if (expression.resolve() instanceof PsiClass) {
        return UsageType.CLASS_STATIC_MEMBER_ACCESS;
      }
    }

    final PsiParameter psiParameter = PsiTreeUtil.getParentOfType(element, PsiParameter.class);
    if (psiParameter != null) {
      final PsiElement scope = psiParameter.getDeclarationScope();
      if (scope instanceof PsiMethod) return UsageType.CLASS_METHOD_PARAMETER_DECLARATION;
      if (scope instanceof PsiCatchSection) return UsageType.CLASS_CATCH_CLAUSE_PARAMETER_DECLARATION;
      if (scope instanceof PsiForeachStatement) return UsageType.CLASS_LOCAL_VAR_DECLARATION;
      return UsageType.UNCLASSIFIED;
    }

    PsiField psiField = PsiTreeUtil.getParentOfType(element, PsiField.class);
    if (psiField != null) {
      if (PsiTreeUtil.isAncestor(psiField.getTypeElement(), element, true)) return UsageType.CLASS_FIELD_DECLARATION;
    }

    PsiLocalVariable psiLocalVar = PsiTreeUtil.getParentOfType(element, PsiLocalVariable.class);
    if (psiLocalVar != null) {
      if (PsiTreeUtil.isAncestor(psiLocalVar.getTypeElement(), element, true)) return UsageType.CLASS_LOCAL_VAR_DECLARATION;
    }

    PsiMethod psiMethod = PsiTreeUtil.getParentOfType(element, PsiMethod.class);
    if (psiMethod != null) {
      final PsiTypeElement retType = psiMethod.getReturnTypeElement();
      if (retType != null && PsiTreeUtil.isAncestor(retType, element, true)) return UsageType.CLASS_METHOD_RETURN_TYPE;
    }

    final PsiNewExpression psiNewExpression = PsiTreeUtil.getParentOfType(element, PsiNewExpression.class);
    if (psiNewExpression != null) {
      final PsiJavaCodeReferenceElement classReference = psiNewExpression.getClassReference();
      if (classReference != null && PsiTreeUtil.isAncestor(classReference, element, false)) return UsageType.CLASS_NEW_OPERATOR;
    }

    return null;
  }


  private class UsageTypeGroup implements UsageGroup {
    private UsageType myUsageType;

    public void update() {
    }

    public UsageTypeGroup(UsageType usageType) {
      myUsageType = usageType;
    }

    public Icon getIcon(boolean isOpen) {
      return null;
    }

    public String getText(UsageView view) {
      return myUsageType.toString();
    }

    public FileStatus getFileStatus() {
      return null;
    }

    public boolean isValid() { return true; }
    public void navigate(boolean focus) { }
    public boolean canNavigate() { return false; }

    public boolean canNavigateToSource() {
      return false;
    }

    public int compareTo(UsageGroup usageGroup) {
      return getText(null).compareTo(usageGroup.getText(null));
    }

    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof UsageTypeGroup)) return false;
      final UsageTypeGroup usageTypeGroup = (UsageTypeGroup)o;
      if (myUsageType != null ? !myUsageType.equals(usageTypeGroup.myUsageType) : usageTypeGroup.myUsageType != null) return false;
      return true;
    }

    public int hashCode() {
      return (myUsageType != null ? myUsageType.hashCode() : 0);
    }
  }
}
