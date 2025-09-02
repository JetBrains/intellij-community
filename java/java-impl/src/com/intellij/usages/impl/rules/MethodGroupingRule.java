// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.usages.impl.rules;

import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.navigation.NavigationItemFileStatus;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataSink;
import com.intellij.openapi.actionSystem.UiDataProvider;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.Segment;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiFormatUtilBase;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.usageView.UsageInfo;
import com.intellij.usages.*;
import com.intellij.usages.rules.PsiElementUsage;
import com.intellij.usages.rules.SingleParentUsageGroupingRule;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Objects;

public class MethodGroupingRule extends SingleParentUsageGroupingRule {
  private static final Logger LOG = Logger.getInstance(MethodGroupingRule.class);
  private final @NotNull UsageViewSettings myUsageViewSettings;

  public MethodGroupingRule(@NotNull UsageViewSettings usageViewSettings) {
    myUsageViewSettings = usageViewSettings;
  }

  @Override
  protected @Nullable UsageGroup getParentGroupFor(@NotNull Usage usage, UsageTarget @NotNull [] targets) {
    if (!(usage instanceof PsiElementUsage)) return null;
    PsiElement psiElement = ((PsiElementUsage)usage).getElement();
    PsiFile containingFile = psiElement.getContainingFile();
    if (containingFile == null) return null;
    InjectedLanguageManager manager = InjectedLanguageManager.getInstance(containingFile.getProject());
    PsiFile topLevelFile = manager.getTopLevelFile(containingFile);
    if (topLevelFile instanceof PsiJavaFile && !topLevelFile.getFileType().isBinary()) {
      PsiElement containingMethod = topLevelFile == containingFile ? psiElement : manager.getInjectionHost(containingFile);
      if (usage instanceof UsageInfo2UsageAdapter && topLevelFile == containingFile) {
        int offset = ((UsageInfo2UsageAdapter)usage).getUsageInfo().getNavigationOffset();
        containingMethod = containingFile.findElementAt(offset);
      }
      do {
        containingMethod = PsiTreeUtil.getParentOfType(containingMethod, PsiMethod.class, true);
        if (containingMethod == null) break;
        final PsiClass containingClass = ((PsiMethod)containingMethod).getContainingClass();
        if (containingClass == null || containingClass.getQualifiedName() != null) break;
      }
      while (true);

      if (containingMethod != null) {
        return new MethodUsageGroup((PsiMethod)containingMethod, myUsageViewSettings);
      }
    }
    return null;
  }

  private static class MethodUsageGroup implements UsageGroup, UiDataProvider {
    private final SmartPsiElementPointer<PsiMethod> myMethodPointer;
    private final @NlsSafe String myName;
    private final Icon myIcon;
    private final Project myProject;

    private final @NotNull UsageViewSettings myUsageViewSettings;

    MethodUsageGroup(PsiMethod psiMethod, @NotNull UsageViewSettings usageViewSettings) {
      myName = PsiFormatUtil.formatMethod(
          psiMethod,
          PsiSubstitutor.EMPTY,
          PsiFormatUtilBase.SHOW_NAME | PsiFormatUtilBase.SHOW_PARAMETERS,
          PsiFormatUtilBase.SHOW_TYPE
        );
      myProject = psiMethod.getProject();
      myMethodPointer = SmartPointerManager.getInstance(myProject).createSmartPsiElementPointer(psiMethod);

      myIcon = getIconImpl(psiMethod);

      myUsageViewSettings = usageViewSettings;
    }

    private static Icon getIconImpl(PsiMethod psiMethod) {
      return psiMethod.getIcon(Iconable.ICON_FLAG_VISIBILITY | Iconable.ICON_FLAG_READ_STATUS);
    }

    @Override
    public int hashCode() {
      return myName.hashCode();
    }

    @Override
    public boolean equals(Object object) {
      if (!(object instanceof MethodUsageGroup group)) {
        return false;
      }
      return Objects.equals(myName, group.myName)
             && SmartPointerManager.getInstance(myProject).pointToTheSameElement(myMethodPointer, group.myMethodPointer);
    }

    @Override
    public Icon getIcon() {
      return myIcon;
    }

    private PsiMethod getMethod() {
      return myMethodPointer.getElement();
    }

    @Override
    public @NotNull String getPresentableGroupText() {
      return myName;
    }

    @Override
    public FileStatus getFileStatus() {
      if (myMethodPointer.getProject().isDisposed()) return null;
      PsiFile file = myMethodPointer.getContainingFile();
      return file == null ? null : NavigationItemFileStatus.get(file);
    }

    @Override
    public boolean isValid() {
      final PsiMethod method = getMethod();
      return method != null && method.isValid();
    }

    @Override
    public void navigate(boolean focus) throws UnsupportedOperationException {
      if (canNavigate()) {
          getMethod().navigate(focus);
      }
    }

    @Override
    public boolean canNavigate() {
      return isValid();
    }

    @Override
    public boolean canNavigateToSource() {
      return canNavigate();
    }

    @Override
    public int compareTo(@NotNull UsageGroup usageGroup) {
      if (!(usageGroup instanceof MethodUsageGroup)) {
        LOG.error("MethodUsageGroup expected but " + usageGroup.getClass() + " found");
      }
      MethodUsageGroup other = (MethodUsageGroup)usageGroup;
      if (SmartPointerManager.getInstance(myProject).pointToTheSameElement(myMethodPointer, other.myMethodPointer)) {
        return 0;
      }
      if (!myUsageViewSettings.isSortAlphabetically()) {
        Segment segment1 = myMethodPointer.getRange();
        Segment segment2 = other.myMethodPointer.getRange();
        if (segment1 != null && segment2 != null) {
          return segment1.getStartOffset() - segment2.getStartOffset();
        }
      }

      return myName.compareToIgnoreCase(other.myName);
    }

    @Override
    public void uiDataSnapshot(@NotNull DataSink sink) {
      sink.lazy(CommonDataKeys.PSI_ELEMENT, () -> {
        return getMethod();
      });
      sink.lazy(UsageView.USAGE_INFO_KEY, () -> {
        PsiMethod method = getMethod();
        return method == null ? null : new UsageInfo(method);
      });
    }
  }
}