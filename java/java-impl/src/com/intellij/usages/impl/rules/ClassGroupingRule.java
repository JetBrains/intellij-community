// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.usages.impl.rules;

import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.navigation.NavigationItemFileStatus;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.util.FileTypeUtils;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.usageView.UsageInfo;
import com.intellij.usages.Usage;
import com.intellij.usages.UsageGroup;
import com.intellij.usages.UsageTarget;
import com.intellij.usages.UsageView;
import com.intellij.usages.rules.PsiElementUsage;
import com.intellij.usages.rules.SingleParentUsageGroupingRule;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

class ClassGroupingRule extends SingleParentUsageGroupingRule implements DumbAware {
  @Nullable
  @Override
  protected UsageGroup getParentGroupFor(@NotNull Usage usage, UsageTarget @NotNull [] targets) {
    if (!(usage instanceof PsiElementUsage)) {
      return null;
    }
    final PsiElement psiElement = ((PsiElementUsage)usage).getElement();
    final PsiFile containingFile = psiElement.getContainingFile();
    if (containingFile == null) return null;

    PsiFile topLevelFile = InjectedLanguageManager.getInstance(containingFile.getProject()).getTopLevelFile(containingFile);

    if (!(topLevelFile instanceof PsiJavaFile) || topLevelFile instanceof ServerPageFile) {
      return null;
    }
    PsiElement containingClass = topLevelFile == containingFile ? psiElement : InjectedLanguageManager
          .getInstance(containingFile.getProject()).getInjectionHost(containingFile);
    do {
      containingClass = PsiTreeUtil.getParentOfType(containingClass, PsiClass.class, true);
    }
    while (containingClass != null && ((PsiClass)containingClass).getQualifiedName() == null);

    if (containingClass == null) {
      // check whether the element is in the import list
      PsiImportList importList = PsiTreeUtil.getParentOfType(psiElement, PsiImportList.class, true);
      if (importList != null) {
        final String fileName = getFileNameWithoutExtension(topLevelFile);
        final PsiClass[] classes = ((PsiJavaFile)topLevelFile).getClasses();
        for (final PsiClass aClass : classes) {
          if (fileName.equals(aClass.getName())) {
            containingClass = aClass;
            break;
          }
        }
      }
    }
    else {
      // skip JspClass synthetic classes.
      if (containingClass.getParent() instanceof PsiFile && FileTypeUtils.isInServerPageFile(containingClass)) {
        containingClass = null;
      }
    }

    if (containingClass != null) {
      return new ClassUsageGroup((PsiClass)containingClass);
    }

    final VirtualFile virtualFile = topLevelFile.getVirtualFile();
    if (virtualFile != null) {
      return new FileGroupingRule.FileUsageGroup(topLevelFile.getProject(), virtualFile);
    }
    return null;
  }

  private static String getFileNameWithoutExtension(final PsiFile file) {
    final String name = file.getName();
    final int index = name.lastIndexOf('.');
    return index < 0? name : name.substring(0, index);
  }

  private static class ClassUsageGroup implements UsageGroup, DataProvider {
    private final SmartPsiElementPointer<PsiClass> myClassPointer;
    private final @NlsSafe String myText;
    private final String myQName;
    private final Icon myIcon;

    ClassUsageGroup(@NotNull PsiClass aClass) {
      myQName = aClass.getQualifiedName();
      myText = createText(aClass);
      myClassPointer = SmartPointerManager.getInstance(aClass.getProject()).createSmartPsiElementPointer(aClass);
      myIcon = aClass.getIcon(Iconable.ICON_FLAG_VISIBILITY | Iconable.ICON_FLAG_READ_STATUS);
    }

    @Override
    public void update() {
    }

    private static @NlsSafe String createText(PsiClass aClass) {
      String text = aClass.getName();
      PsiClass containingClass = aClass.getContainingClass();
      while (containingClass != null) {
        text = containingClass.getName() + '.' + text;
        containingClass = containingClass.getContainingClass();
      }
      return text;
    }

    @Override
    public Icon getIcon(boolean isOpen) {
      return myIcon;
    }

    @Override
    @NotNull
    public String getText(UsageView view) {
      return myText;
    }

    @Override
    public FileStatus getFileStatus() {
      return isValid() ? NavigationItemFileStatus.get(getPsiClass()) : null;
    }

    private PsiClass getPsiClass() {
      return myClassPointer.getElement();
    }

    @Override
    public boolean isValid() {
      PsiClass psiClass = getPsiClass();
      return psiClass != null && psiClass.isValid();
    }

    public int hashCode() {
      return myQName.hashCode();
    }

    public boolean equals(Object object) {
      return object instanceof ClassUsageGroup && myQName.equals(((ClassUsageGroup)object).myQName);
    }

    @Override
    public void navigate(boolean focus) throws UnsupportedOperationException {
      if (canNavigate()) {
          getPsiClass().navigate(focus);
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
      return getText(null).compareToIgnoreCase(usageGroup.getText(null));
    }

    @Nullable
    @Override
    public Object getData(@NotNull String dataId) {
      if (!isValid()) return null;
      if (CommonDataKeys.PSI_ELEMENT.is(dataId)) {
        return getPsiClass();
      }
      else if (UsageView.USAGE_INFO_KEY.is(dataId)) {
        PsiClass psiClass = getPsiClass();
        return psiClass == null ? null : new UsageInfo(psiClass);
      }
      return null;
    }
  }
}
