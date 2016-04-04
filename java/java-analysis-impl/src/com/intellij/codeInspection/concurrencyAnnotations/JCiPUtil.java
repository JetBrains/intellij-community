/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.codeInspection.concurrencyAnnotations;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.ConcurrencyAnnotationsManager;
import com.intellij.psi.*;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.javadoc.PsiDocTag;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class JCiPUtil {
  static boolean isJCiPAnnotation(String ref) {
    return "Immutable".equals(ref) || "GuardedBy".equals(ref) || "ThreadSafe".equals(ref) || "NotThreadSafe".equals(ref);
  }

  private JCiPUtil() {
  }

  public static boolean isImmutable(@NotNull PsiClass aClass) {
    final PsiAnnotation annotation = AnnotationUtil.findAnnotation(aClass, ConcurrencyAnnotationsManager.getInstance(aClass.getProject()).getImmutableAnnotations());
    if (annotation != null) {
      return true;
    }
    PsiDocComment comment = aClass.getDocComment();
    return comment != null && comment.findTagByName("@Immutable") != null;
  }

  @Nullable
  static String findGuardForMember(@NotNull PsiMember member) {
    final PsiAnnotation annotation = AnnotationUtil.findAnnotation(member, ConcurrencyAnnotationsManager.getInstance(member.getProject()).getGuardedByAnnotations());
    if (annotation != null) {
      return getGuardValue(annotation);
    }
    if (member instanceof PsiCompiledElement) {
      member = (PsiMember)member.getNavigationElement();
      if (member == null || member instanceof PsiCompiledElement) {
        return null; // can't analyze compiled code
      }
    }
    final GuardedTagVisitor visitor = new GuardedTagVisitor();
    member.accept(visitor);
    return visitor.getGuardString();
  }

  static boolean isGuardedBy(@NotNull PsiMember member, String guard) {

    final PsiAnnotation annotation = AnnotationUtil.findAnnotation(member, ConcurrencyAnnotationsManager.getInstance(member.getProject()).getGuardedByAnnotations());
    if (annotation != null) {
      final PsiAnnotationParameterList parameters = annotation.getParameterList();
      final PsiNameValuePair[] pairs = parameters.getAttributes();
      final String fieldName = '"' + guard + '"';
      for (PsiNameValuePair pair : pairs) {
        final String name = pair.getName();
        if ("value".equals(name) || name == null) {
          final PsiAnnotationMemberValue value = pair.getValue();
          if (value != null && value.getText().equals(fieldName)) {
            return true;
          }
        }
      }
    }
    return false;
  }

  public static boolean isGuardedBy(PsiMember member, PsiField field) {
    return isGuardedBy(member, field.getName());
  }

  static boolean isGuardedByAnnotation(@NotNull PsiAnnotation annotation) {
    return ConcurrencyAnnotationsManager.getInstance(annotation.getProject()).getGuardedByAnnotations().contains(annotation.getQualifiedName());
  }

  static boolean isGuardedByTag(PsiDocTag tag) {
    final String text = tag.getText();

    return text.startsWith("@GuardedBy") && text.contains("(") && text.contains(")");
  }

  @Nullable
  static String getGuardValue(PsiAnnotation annotation) {
    final PsiAnnotationParameterList parameters = annotation.getParameterList();
    final PsiNameValuePair[] pairs = parameters.getAttributes();
    for (PsiNameValuePair pair : pairs) {
      final String name = pair.getName();
      if ("value".equals(name) || name == null) {
        final PsiAnnotationMemberValue psiAnnotationMemberValue = pair.getValue();
        if (psiAnnotationMemberValue != null) {
          final String value = psiAnnotationMemberValue.getText();
          final String trim = value.substring(1, value.length() - 1).trim();
          if (trim.equals("itself")) {
            final PsiMember member = PsiTreeUtil.getParentOfType(annotation, PsiMember.class);
            if (member != null) return member.getName();
          }
          return trim;
        }
      }
    }
    return null;
  }

  @NotNull
  static String getGuardValue(PsiDocTag tag) {
    final String text = tag.getText();
    return text.substring(text.indexOf((int)'(') + 1, text.indexOf((int)')')).trim();
  }

  private static class GuardedTagVisitor extends JavaRecursiveElementWalkingVisitor {
    private String guardString;

    @Override
    public void visitDocTag(PsiDocTag tag) {
      super.visitDocTag(tag);
      final String text = tag.getText();
      if (text.startsWith("@GuardedBy") && text.contains("(") && text.contains(")")) {
        guardString = text.substring(text.indexOf((int)'(') + 1, text.indexOf((int)')'));
      }
    }

    @Nullable
    private String getGuardString() {
      return guardString;
    }
  }
}
