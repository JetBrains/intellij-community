// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.hierarchy;

import com.intellij.ide.util.treeView.AlphaComparator;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.ide.util.treeView.SourceComparator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.psi.PsiAnonymousClass;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassOwner;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiImplicitClass;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;


public final class JavaHierarchyUtil {
  private JavaHierarchyUtil() { }

  public static @Nullable @NlsSafe String getPackageName(@NotNull PsiClass psiClass) {
    return PsiUtil.getPackageName(psiClass);
  }

  public static @NotNull Comparator<NodeDescriptor<?>> getComparator(@NotNull Project project) {
    HierarchyBrowserManager.State state = HierarchyBrowserManager.getInstance(project).getState();
    return state != null && state.SORT_ALPHABETICALLY ? AlphaComparator.getInstance() : SourceComparator.INSTANCE;
  }

  /**
   * Returns a dot-separated qualified path to an element's container.
   * Anonymous and implicit classes are not included in the returned path.
   * <p>
   * Examples:
   * <ul>
   *   <li>For method {@code "com.example.MyClass.myMethod()"} returns {@code "com.example.MyClass"}</li>
   *   <li>For method {@code "com.example.MyClass.outerMethod().LocalClass.myMethod()"} returns {@code "com.example.MyClass.outerMethod().LocalClass"}</li>
   *   <li>For top-level class: {@code "com.example.MyClass"} returns the package {@code "com.example"}</li>
   * </ul>
   */
  public static @Nls @NotNull String getElementLocationPath(@NotNull PsiElement element) {
    String nesting = null;
    PsiMember current = PsiTreeUtil.getStubOrPsiParentOfType(element, PsiMember.class);
    while (current != null) {
      if (current instanceof PsiMethod psiMethod) {
        nesting = concat(psiMethod.getName() + "()", nesting);
      }
      else if (current instanceof PsiField psiField) {
        nesting = concat(psiField.getName(), nesting);
      }
      else if (current instanceof PsiClass && !(current instanceof PsiAnonymousClass) && !(current instanceof PsiImplicitClass)) {
        String name = current.getName();
        if (name != null) {
          nesting = concat(name, nesting);
        }
      }
      current = PsiTreeUtil.getStubOrPsiParentOfType(current, PsiMember.class);
    }

    PsiFile file = element.getContainingFile();
    String packageName = "";
    if (file instanceof PsiClassOwner psiClassOwner) {
      packageName = psiClassOwner.getPackageName();
    }

    if (packageName.isEmpty()) {
      return nesting == null ? "" : nesting;
    }
    return nesting == null ? packageName : packageName + "." + nesting;
  }

  private static String concat(String name, String nesting) {
    return nesting == null ? name : name + "." + nesting;
  }
}