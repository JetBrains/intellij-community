// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.changeSignature.inCallers;

import com.intellij.ide.hierarchy.JavaHierarchyUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.psi.*;
import com.intellij.psi.presentation.java.ClassPresentationUtil;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiFormatUtilBase;
import com.intellij.refactoring.changeSignature.MemberNodeBase;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.JBColor;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

public abstract class JavaMemberNode<M extends PsiMember> extends MemberNodeBase<M> {
  protected JavaMemberNode(final M member, Set<M> called, Project project, Runnable cancelCallback) {
    super(member, called, project, cancelCallback);
  }

  @Override
  protected void customizeRendererText(ColoredTreeCellRenderer renderer) {
    customizeRendererText(renderer, getMember(), isEnabled());
  }

  public static <M extends PsiMember> void customizeRendererText(ColoredTreeCellRenderer renderer, M member, boolean enabled) {
    final @NlsSafe StringBuilder buffer = new StringBuilder(128);
    final PsiClass containingClass = member.getContainingClass();
    if (containingClass != null) {
      buffer.append(ClassPresentationUtil.getNameForClass(containingClass, false));
      buffer.append('.');
    }
    buffer.append(formatMember(member));

    final SimpleTextAttributes attributes = enabled ?
                                            new SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, UIUtil.getTreeForeground()) :
                                            SimpleTextAttributes.EXCLUDED_ATTRIBUTES;
    renderer.append(buffer.toString(), attributes);

    if (containingClass != null) {

      final String packageName = JavaHierarchyUtil.getPackageName(containingClass);
      renderer.append("  (" + packageName + ")", new SimpleTextAttributes(SimpleTextAttributes.STYLE_ITALIC, JBColor.GRAY));
    }
  }

  private static String formatMember(@NotNull PsiMember member) {
    if (member instanceof PsiMethod) {
      return PsiFormatUtil.formatMethod(
        (PsiMethod)member,
        PsiSubstitutor.EMPTY, PsiFormatUtilBase.SHOW_NAME | PsiFormatUtilBase.SHOW_PARAMETERS,
        PsiFormatUtilBase.SHOW_TYPE
      );
    } else if (member instanceof PsiField){
      return PsiFormatUtil.formatVariable(
        (PsiField)member,
        PsiFormatUtilBase.SHOW_NAME | PsiFormatUtilBase.SHOW_TYPE,
        PsiSubstitutor.EMPTY
      );
    }
    else {
      return PsiFormatUtil.formatClass((PsiClass)member, PsiFormatUtilBase.SHOW_NAME);
    }
  }
}
