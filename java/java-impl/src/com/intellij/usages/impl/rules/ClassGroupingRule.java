/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.navigation.NavigationItemFileStatus;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.actionSystem.DataSink;
import com.intellij.openapi.actionSystem.TypeSafeDataProvider;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.usageView.UsageInfo;
import com.intellij.usages.Usage;
import com.intellij.usages.UsageGroup;
import com.intellij.usages.UsageView;
import com.intellij.usages.rules.PsiElementUsage;
import com.intellij.usages.rules.UsageGroupingRule;
import com.intellij.psi.util.FileTypeUtils;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author max
 */
public class ClassGroupingRule implements UsageGroupingRule, DumbAware {
  @Override
  public UsageGroup groupUsage(@NotNull Usage usage) {
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
      if (containingClass == null || ((PsiClass)containingClass).getQualifiedName() != null) break;
    }
    while (true);

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

  private static class ClassUsageGroup implements UsageGroup, TypeSafeDataProvider {
    private final SmartPsiElementPointer myClassPointer;
    private final String myText;
    private final String myQName;
    private final Icon myIcon;

    public ClassUsageGroup(@NotNull PsiClass aClass) {
      myQName = aClass.getQualifiedName();
      myText = createText(aClass);
      myClassPointer = SmartPointerManager.getInstance(aClass.getProject()).createSmartPsiElementPointer(aClass);
      myIcon = aClass.getIcon(Iconable.ICON_FLAG_VISIBILITY | Iconable.ICON_FLAG_READ_STATUS);
    }

    @Override
    public void update() {
    }

    private static String createText(PsiClass aClass) {
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
      return (PsiClass)myClassPointer.getElement();
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

    @Override
    public void calcData(final DataKey key, final DataSink sink) {
      if (!isValid()) return;
      if (CommonDataKeys.PSI_ELEMENT == key) {
        sink.put(CommonDataKeys.PSI_ELEMENT, getPsiClass());
      }
      if (UsageView.USAGE_INFO_KEY == key) {
        PsiClass psiClass = getPsiClass();
        if (psiClass != null) {
          sink.put(UsageView.USAGE_INFO_KEY, new UsageInfo(psiClass));
        }
      }
    }
  }
}
