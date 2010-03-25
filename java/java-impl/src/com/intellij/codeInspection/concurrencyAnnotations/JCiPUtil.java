/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import com.intellij.psi.*;
import com.intellij.psi.javadoc.PsiDocTag;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class JCiPUtil {
  @NonNls
  private static final String IMMUTABLE = "net.jcip.annotations.Immutable";
  @NonNls
  private static final String GUARDED_BY = "net.jcip.annotations.GuardedBy";
  @NonNls
  private static final String THREAD_SAFE = "net.jcip.annotations.ThreadSafe";

  public static boolean isJCiPAnnotation(String ref) {
    return "Immutable".equals(ref) || "GuardedBy".equals(ref) || "ThreadSafe".equals("ref");
  }

  private JCiPUtil() {
  }

  public static boolean isImmutable(PsiClass aClass) {
    final PsiAnnotation annotation = AnnotationUtil.findAnnotation(aClass, IMMUTABLE);
    if (annotation != null) {
      return true;
    }
    final ImmutableTagVisitor visitor = new ImmutableTagVisitor();
    aClass.accept(visitor);
    return visitor.isFound();
  }

  @Nullable
  public static String findGuardForMember(PsiMember member) {
    final PsiAnnotation annotation = AnnotationUtil.findAnnotation(member, GUARDED_BY);
    if (annotation != null) {
      return getGuardValue(annotation);
    }

    final GuardedTagVisitor visitor = new GuardedTagVisitor();
    member.accept(visitor);
    return visitor.getGuardString();
  }

  public static boolean isGuardedBy(PsiMember member, String guard) {

    final PsiAnnotation annotation = AnnotationUtil.findAnnotation(member, GUARDED_BY);
    if (annotation != null) {
      final PsiAnnotationParameterList parameters = annotation.getParameterList();
      final PsiNameValuePair[] pairs = parameters.getAttributes();
      final String fieldName = '"' + guard + '"';
      for (PsiNameValuePair pair : pairs) {
        final String name = pair.getName();
        if (("value".equals(name) || name == null)) {
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

  public static boolean isGuardedByAnnotation(PsiAnnotation annotation) {
    return GUARDED_BY.equals(annotation.getQualifiedName());
  }

  public static boolean isGuardedByTag(PsiDocTag tag) {
    final String text = tag.getText();

    return text.startsWith("@GuardedBy") && text.contains("(") && text.contains(")");
  }

  @Nullable
  public static String getGuardValue(PsiAnnotation annotation) {
    final PsiAnnotationParameterList parameters = annotation.getParameterList();
    final PsiNameValuePair[] pairs = parameters.getAttributes();
    for (PsiNameValuePair pair : pairs) {
      final String name = pair.getName();
      if ("value".equals(name) || name == null) {
        final PsiAnnotationMemberValue psiAnnotationMemberValue = pair.getValue();
        if (psiAnnotationMemberValue != null) {
          final String value = psiAnnotationMemberValue.getText();
          return value.substring(1, value.length() - 1).trim();
        }
      }
    }
    return null;
  }

  @NotNull
  public static String getGuardValue(PsiDocTag tag) {
    final String text = tag.getText();
    return text.substring(text.indexOf((int)'(') + 1, text.indexOf((int)')')).trim();
  }

  private static class GuardedTagVisitor extends JavaRecursiveElementVisitor {
    private String guardString = null;

    public void visitDocTag(PsiDocTag tag) {
      super.visitDocTag(tag);
      final String text = tag.getText();
      if (text.startsWith("@GuardedBy") && text.contains("(") && text.contains(")")) {
        guardString = text.substring(text.indexOf((int)'(') + 1, text.indexOf((int)')'));
      }
    }

    @Nullable
    public String getGuardString() {
      return guardString;
    }
  }

  private static class ImmutableTagVisitor extends JavaRecursiveElementWalkingVisitor {
    private boolean found = false;

    public void visitDocTag(PsiDocTag tag) {
      super.visitDocTag(tag);
      final String text = tag.getText();
      if (text.startsWith("@Immutable")) {
        found = true;
      }
    }

    public boolean isFound() {
      return found;
    }
  }
}
