/*
 * Copyright 2000-2007 JetBrains s.r.o.
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

import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.actionSystem.DataKeys;
import com.intellij.openapi.actionSystem.DataSink;
import com.intellij.openapi.actionSystem.TypeSafeDataProvider;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.usageView.UsageInfo;
import com.intellij.usages.Usage;
import com.intellij.usages.UsageGroup;
import com.intellij.usages.UsageView;
import com.intellij.usages.rules.PsiElementUsage;
import com.intellij.usages.rules.UsageGroupingRule;

import javax.swing.*;

/**
 * @author max
 */
public class MethodGroupingRule implements UsageGroupingRule {
  private static final Logger LOG = Logger.getInstance("#com.intellij.usages.impl.rules.MethodGroupingRule");

  public UsageGroup groupUsage(Usage usage) {
    if (usage instanceof PsiElementUsage) {
      PsiElement psiElement = ((PsiElementUsage)usage).getElement();
      if (psiElement.getContainingFile() instanceof PsiJavaFile) {
        PsiElement containingMethod = psiElement;
        do {
          containingMethod = PsiTreeUtil.getParentOfType(containingMethod, PsiMethod.class, true);
          if (containingMethod == null || ((PsiMethod)containingMethod).getContainingClass().getQualifiedName() != null) break;
        }
        while (true);

        if (containingMethod != null) {
          return new MethodUsageGroup((PsiMethod)containingMethod);
        }
      }
    }
    return null;
  }

  private static class MethodUsageGroup implements UsageGroup, TypeSafeDataProvider {
    private SmartPsiElementPointer myMethodPointer;
    private String myName;
    private Icon myIcon;

    public MethodUsageGroup(PsiMethod psiMethod) {
      myName = PsiFormatUtil.formatMethod(
          psiMethod,
          PsiSubstitutor.EMPTY,
          PsiFormatUtil.SHOW_NAME | PsiFormatUtil.SHOW_PARAMETERS,
          PsiFormatUtil.SHOW_TYPE
        );
      myMethodPointer = SmartPointerManager.getInstance(psiMethod.getProject()).createLazyPointer(psiMethod);
      update();
    }

    public void update() {
      if (isValid()) {
        myIcon = getIconImpl(getMethod());
      }
    }

    private static Icon getIconImpl(PsiMethod psiMethod) {
      return psiMethod.getIcon(Iconable.ICON_FLAG_VISIBILITY | Iconable.ICON_FLAG_READ_STATUS);
    }

    public int hashCode() {
      return myName.hashCode();
    }

    public boolean equals(Object object) {
      if (!(object instanceof MethodUsageGroup)) {
        return false;
      }
      MethodUsageGroup group = (MethodUsageGroup) object;
      if (isValid() && group.isValid()) {
        return getMethod().getManager().areElementsEquivalent(getMethod(), group.getMethod());
      }
      return Comparing.equal(myName, ((MethodUsageGroup)object).myName);
    }

    public Icon getIcon(boolean isOpen) {
      return myIcon;
    }

    private PsiMethod getMethod() {
      return (PsiMethod)myMethodPointer.getElement();
    }

    public String getText(UsageView view) {
      return myName;
    }

    public FileStatus getFileStatus() {
      return isValid() ? getMethod().getFileStatus() : null;
    }

    public boolean isValid() {
      final PsiMethod method = getMethod();
      return method != null && method.isValid();
    }

    public void navigate(boolean focus) throws UnsupportedOperationException {
      if (canNavigate()) {
          getMethod().navigate(focus);
      }
    }

    public boolean canNavigate() {
      return isValid();
    }

    public boolean canNavigateToSource() {
      return canNavigate();
    }

    public int compareTo(UsageGroup usageGroup) {
      if (!(usageGroup instanceof MethodUsageGroup)) {
        LOG.error("MethodUsageGroup expected but " + usageGroup.getClass() + " found");
      }

      return myName.compareTo(((MethodUsageGroup)usageGroup).myName);
    }

    public void calcData(final DataKey key, final DataSink sink) {
      if (!isValid()) return;
      if (DataKeys.PSI_ELEMENT == key) {
        sink.put(DataKeys.PSI_ELEMENT, getMethod());
      }
      if (UsageView.USAGE_INFO_KEY == key) {
        PsiMethod method = getMethod();
        if (method != null) {
          sink.put(UsageView.USAGE_INFO_KEY, new UsageInfo(method));
        }
      }
    }
  }
}
