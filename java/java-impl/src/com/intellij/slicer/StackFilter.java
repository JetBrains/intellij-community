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
import com.intellij.psi.util.ClassUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.psiutils.ClassUtils;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;

/**
 * Allows to narrow dataflow-to-here results based on known stacktrace.
 */
final class StackFilter {
  final int myExtraFrames;
  final @NotNull String myClassName;
  final @NotNull String myMethodName;
  final @Nullable String myFileName;
  final @Nullable StackFilter myNext;

  private StackFilter(int frames,
                      @NotNull String className,
                      @NotNull String methodName,
                      @Nullable String fileName, @Nullable StackFilter next) {
    myExtraFrames = frames;
    myClassName = className;
    myMethodName = methodName;
    myFileName = fileName;
    myNext = next;
  }

  SearchScope correctScope(SearchScope base) {
    if (base instanceof GlobalSearchScope && myExtraFrames == 0 && myFileName != null) {
      return new DelegatingGlobalSearchScope((GlobalSearchScope)base) {
        @Override
        public boolean contains(@NotNull VirtualFile file) {
          return file.getName().equals(myFileName) && super.contains(file);
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
    return new StackFilter(myExtraFrames + 1, myClassName, myMethodName, myFileName, myNext);
  }

  @Nullable StackFilter popFrame(Project project) {
    if (myExtraFrames == 0) {
      if (myNext != null && myClassName.equals(myNext.myClassName) &&
          myMethodName.equals(myNext.myMethodName) && Objects.equals(myFileName, myNext.myFileName)) {
        PsiClass psiClass = ClassUtil.findPsiClass(PsiManager.getInstance(project), myClassName, null, true);
        if (psiClass != null) {
          PsiMethod[] methods = psiClass.findMethodsByName(myMethodName, false);
          for (PsiMethod method : methods) {
            if (isBridge(method)) return myNext.myNext;
          }
        }
      }
      return myNext;
    }
    return new StackFilter(myExtraFrames - 1, myClassName, myMethodName, myFileName, myNext);
  }

  private static boolean isBridge(PsiMethod method) {
    PsiMethod[] superMethods = method.findSuperMethods();
    if (superMethods.length == 0) return false;
    PsiType returnType = TypeConversionUtil.erasure(method.getReturnType());
    List<PsiType> parameterTypes = ContainerUtil.map(method.getParameterList().getParameters(),
                                                     p -> TypeConversionUtil.erasure(p.getType()));
    for (PsiMethod superMethod : superMethods) {
      if (!Objects.equals(returnType, TypeConversionUtil.erasure(superMethod.getReturnType()))) {
        return true;
      }
      PsiParameter[] parameters = superMethod.getParameterList().getParameters();
      if (parameters.length == parameterTypes.size()) {
        for (int i = 0; i < parameters.length; i++) {
          if (!Objects.equals(TypeConversionUtil.erasure(parameters[i].getType()), parameterTypes.get(i))) {
            return true;
          }
        }
      }
    }
    return false;
  }

  static @Nullable StackFilter from(List<ExceptionAnalysisProvider.StackLine> list) {
    return StreamEx.of(list).foldRight(null, (line, prev) ->
      new StackFilter(0, line.getClassName(), line.getMethodName(), line.getFileName(), prev));
  }

  @Override
  public String toString() {
    return (myExtraFrames == 0 ? "" : myExtraFrames + "+") + myClassName + "." + myMethodName;
  }
}
