package com.intellij.usages.impl.rules;

import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Iconable;
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
 * Date: Dec 20, 2004
 * Time: 11:06:30 AM
 * To change this template use File | Settings | File Templates.
 */
public class ClassGroupingRule implements UsageGroupingRule {
  private static final Logger LOG = Logger.getInstance("#com.intellij.usages.impl.rules.ClassGroupingRule");

  public UsageGroup groupUsage(Usage usage) {
    if (usage instanceof PsiElementUsage) {
      PsiElement psiElement = ((PsiElementUsage)usage).getElement();
      if (psiElement.getContainingFile() instanceof PsiJavaFile) {
        PsiElement containingClass = psiElement;
        do {
          containingClass = PsiTreeUtil.getParentOfType(containingClass, PsiClass.class, true);
          if (containingClass == null || ((PsiClass)containingClass).getQualifiedName() != null) break;
        }
        while (true);

        if (containingClass != null) {
          return new ClassUsageGroup((PsiClass)containingClass);
        }
      }
    }
    return null;
  }

  private static class ClassUsageGroup implements UsageGroup, DataProvider {
    private SmartPsiElementPointer myClassPointer;
    private String myText;
    private String myQName;
    private Icon myIcon;

    public ClassUsageGroup(PsiClass aClass) {
      myQName = aClass.getQualifiedName();
      myText = createText(aClass);
      myIcon = getIconImpl(aClass);
      myClassPointer = SmartPointerManager.getInstance(aClass.getProject()).createLazyPointer(aClass);
    }

    private Icon getIconImpl(PsiClass aClass) {
      return aClass.getIcon(Iconable.ICON_FLAG_VISIBILITY | Iconable.ICON_FLAG_READ_STATUS);
    }

    private String createText(PsiClass aClass) {
      String text = aClass.getName();
      PsiClass containingClass = aClass.getContainingClass();
      while (containingClass != null) {
        text = containingClass.getName() + '.' + text;
        containingClass = containingClass.getContainingClass();
      }
      return text;
    }

    public Icon getIcon(boolean isOpen) {
      return isValid() ? getIconImpl(getPsiClass()) : myIcon;
    }

    public String getText(UsageView view) {
      return myText;
    }

    public FileStatus getFileStatus() {
      return isValid() ? (getPsiClass()).getFileStatus() : null;
    }

    private PsiClass getPsiClass() {
      return (PsiClass)myClassPointer.getElement();
    }

    public boolean isValid() {
      return getPsiClass() != null;
    }

    public int hashCode() {
      return myQName.hashCode();
    }

    public boolean equals(Object object) {
      return myQName.equals(((ClassUsageGroup)object).myQName);
    }

    public void navigate(boolean focus) throws UnsupportedOperationException {
      if (canNavigate()) {
          getPsiClass().navigate(focus);
      }
    }

    public boolean canNavigate() {
      return isValid();
    }

    public int compareTo(UsageGroup usageGroup) {
      return getText(null).compareTo(usageGroup.getText(null));
    }

    public Object getData(String dataId) {
      if (dataId.equals(DataConstants.PSI_ELEMENT)) {
        return getPsiClass();
      }

      return null;
    }
  }
}
