/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.refactoring.changeSignature.inCallers;

import com.intellij.ide.hierarchy.JavaHierarchyUtil;
import com.intellij.openapi.project.Project;
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
    final StringBuilder buffer = new StringBuilder(128);
    final PsiClass containingClass = getMember().getContainingClass();
    if (containingClass != null) {
      buffer.append(ClassPresentationUtil.getNameForClass(containingClass, false));
      buffer.append('.');
    }
    buffer.append(formatMember(getMember()));

    final SimpleTextAttributes attributes = isEnabled() ?
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
    } else {
      assert member instanceof PsiField;
      return PsiFormatUtil.formatVariable(
        (PsiField)member,
        PsiFormatUtilBase.SHOW_NAME | PsiFormatUtilBase.SHOW_TYPE,
        PsiSubstitutor.EMPTY
      );
    }
  }
}
