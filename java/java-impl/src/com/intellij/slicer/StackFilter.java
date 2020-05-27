// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.slicer;

import com.intellij.execution.filters.ExceptionAnalysisProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.DelegatingGlobalSearchScope;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.ig.psiutils.ClassUtils;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Allows to narrow dataflow-to-here results based on known stacktrace.
 * TODO: support bridge methods
 */
class StackFilter {
  final int myExtraFrames;
  final @NotNull String myClassName;
  final @NotNull String myMethodName;
  final @Nullable StackFilter myNext;

  private StackFilter(int frames,
                      @NotNull String className,
                      @NotNull String methodName,
                      @Nullable StackFilter next) {
    myExtraFrames = frames;
    myClassName = className;
    myMethodName = methodName;
    myNext = next;
  }
  
  SearchScope correctScope(Project project, SearchScope base) {
    if (base instanceof GlobalSearchScope && myExtraFrames == 0) {
      PsiManager instance = PsiManager.getInstance(project);
      String packageName = StringUtil.getPackageName(myClassName);
      return new DelegatingGlobalSearchScope((GlobalSearchScope)base) {

        @Override
        public boolean contains(@NotNull VirtualFile file) {
          if (!super.contains(file)) return false;
          PsiFile psiFile = instance.findFile(file);
          if (!(psiFile instanceof PsiClassOwner)) return false;
          // Do not filter by exact class for now
          return ((PsiClassOwner)psiFile).getPackageName().equals(packageName);
        }
      };
    }
    return base;
  }

  boolean isAcceptable(PsiElement element) {
    if (myExtraFrames > 0) return true;
    PsiElement parent = getElementContext(element);
    if (parent instanceof PsiMember) {
      return myMethodName.equals(getExpectedName((PsiMember)parent)) && classMatches(((PsiMember)parent).getContainingClass());
    }
    if (parent instanceof PsiLambdaExpression) {
      return myMethodName.startsWith("lambda$") && classMatches(ClassUtils.getContainingClass(parent));
    }
    return false;
  }

  static PsiElement getElementContext(PsiElement element) {
    PsiElement parent;
    while(true) {
      parent = PsiTreeUtil.getParentOfType(element, PsiMember.class, PsiLambdaExpression.class);
      if (parent instanceof PsiAnonymousClass && PsiTreeUtil.isAncestor(((PsiAnonymousClass)parent).getArgumentList(), element, true)) {
        element = parent;
      } else {
        break;
      }
    }
    return parent;
  }

  private static @Nullable String getExpectedName(PsiMember member) {
    if (member instanceof PsiMethod) {
      return ((PsiMethod)member).isConstructor() ? "<init>" : member.getName();
    }
    if (member instanceof PsiField || member instanceof PsiClassInitializer) {
      return member.hasModifierProperty(PsiModifier.STATIC) ? "<clinit>" : "<init>";
    }
    return null;
  }

  private boolean classMatches(PsiClass aClass) {
    if (aClass == null) return false;
    PsiFile file = aClass.getContainingFile();
    if (!(file instanceof PsiClassOwner)) return false;
    String packageName = StringUtil.getPackageName(myClassName);
    if (!((PsiClassOwner)file).getPackageName().matches(packageName)) return false;
    String shortName = StringUtil.getShortName(myClassName);
    return classNameMatches(aClass, shortName);
  }

  private static boolean classNameMatches(PsiClass aClass, String shortName) {
    String actualName = aClass.getName();
    if (shortName.equals(actualName)) return true;
    String afterDollar = StringUtil.getShortName(shortName, '$');
    if (actualName != null) {
      if (!actualName.equals(afterDollar)) return false;
    } else {
      if (!afterDollar.matches("\\d+")) return false;
    }
    PsiClass containingClass = ClassUtils.getContainingClass(aClass);
    String prefix = StringUtil.substringBefore(shortName, "$");
    if (prefix == null) {
      return containingClass == null;
    }
    return containingClass != null && classNameMatches(containingClass, prefix);
  }

  @NotNull StackFilter pushFrame() {
    return new StackFilter(myExtraFrames + 1, myClassName, myMethodName, myNext);
  }
  
  @Nullable StackFilter popFrame() {
    return myExtraFrames == 0 ? myNext :
           new StackFilter(myExtraFrames - 1, myClassName, myMethodName, myNext);
  }

  static @Nullable StackFilter from(List<ExceptionAnalysisProvider.StackLine> list) {
    return StreamEx.of(list).foldRight(null, (line, prev) -> 
      new StackFilter(0, line.getClassName(), line.getMethodName(), prev));
  }

  @Override
  public String toString() {
    return (myExtraFrames == 0 ? "" : myExtraFrames + "+") + myClassName + "." + myMethodName;
  }
}
