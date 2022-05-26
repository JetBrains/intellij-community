// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Dmitry Avdeev
 */
public class DefaultAnnotationParamInspection extends AbstractBaseJavaLocalInspectionTool {

  /**
   * Allows skipping DefaultAnnotationParamInspection for specific annotations parameters
   */
  public interface IgnoreAnnotationParamSupport {
    ExtensionPointName<IgnoreAnnotationParamSupport> EP_NAME =
      ExtensionPointName.create("com.intellij.lang.jvm.ignoreAnnotationParamSupport");

    /**
     * @param annotationFQN full qualified name of the annotation
     * @param annotationParameterName name of the annotation param
     * @return true to skip inspection for {@code annotationParameterName} and annotation {@code annotationFQN}
     */
    default boolean ignoreAnnotationParam(@Nullable String annotationFQN, @NotNull String annotationParameterName) {
      return false;
    }
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override
      public void visitNameValuePair(final PsiNameValuePair pair) {
        PsiAnnotationMemberValue value = pair.getValue();
        PsiReference reference = pair.getReference();
        if (reference == null) return;
        PsiElement element = reference.resolve();
        if (!(element instanceof PsiAnnotationMethod)) return;

        PsiAnnotationMemberValue defaultValue = ((PsiAnnotationMethod)element).getDefaultValue();
        if (defaultValue == null) return;

        if (AnnotationUtil.equal(value, defaultValue)) {
          PsiElement elementParent = element.getParent();
          if (elementParent instanceof PsiClass) {
            final String qualifiedName = ((PsiClass)elementParent).getQualifiedName();
            final String name = ((PsiAnnotationMethod)element).getName();
            if (ContainerUtil.exists(IgnoreAnnotationParamSupport.EP_NAME.getExtensions(),
                                     ext -> ext.ignoreAnnotationParam(qualifiedName, name))) {
              return;
            }
          }
          holder.registerProblem(value, JavaBundle.message("inspection.message.redundant.default.parameter.value.assignment"), ProblemHighlightType.LIKE_UNUSED_SYMBOL,
                                 createRemoveParameterFix());
        }
      }
    };
  }

  @NotNull
  private static LocalQuickFix createRemoveParameterFix() {
    return new LocalQuickFix() {
      @Nls
      @NotNull
      @Override
      public String getFamilyName() {
        return JavaBundle.message("quickfix.family.remove.redundant.parameter");
      }

      @Override
      public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
        PsiElement parent = descriptor.getPsiElement().getParent();
        parent.delete();
      }
    };
  }
}
