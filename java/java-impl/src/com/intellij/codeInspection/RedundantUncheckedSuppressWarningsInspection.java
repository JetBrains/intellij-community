/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.codeInspection;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.analysis.GenericsHighlightUtil;
import com.intellij.codeInspection.uncheckedWarnings.UncheckedWarningLocalInspection;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.Function;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.regex.Matcher;

/**
 * User: anna
 * Date: 2/14/11
 */
public class RedundantUncheckedSuppressWarningsInspection extends BaseJavaLocalInspectionTool {
  @Nls
  @NotNull
  @Override
  public String getGroupDisplayName() {
    return GroupNames.LANGUAGE_LEVEL_SPECIFIC_GROUP_NAME;
  }
  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return "Redundant unchecked warning suppressed";
  }
  @NotNull
  @Override
  public String getShortName() {
    return "RedundantUncheckedSuppress";
  }
  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override
      public void visitReferenceExpression(PsiReferenceExpression expression) {
      }

      @Override
      public void visitComment(PsiComment comment) {
        super.visitComment(comment);
        if (!PsiUtil.getLanguageLevel(comment).isAtLeast(LanguageLevel.JDK_1_7)) return;
        final HashSet<String> tools2Suppress = new HashSet<String>();
        collectCommentSuppresses(comment, tools2Suppress);
        if (tools2Suppress.contains(RemoveUncheckedWarningFix.UNCHECKED)) {
          final PsiElement statement = PsiTreeUtil.skipSiblingsForward(comment, PsiWhiteSpace.class);
          if (statement instanceof PsiStatement) {
            checkIfSafeToRemoveWarning(comment,  statement, holder);
          }
        }
      }

      @Override
      public void visitAnnotation(PsiAnnotation annotation) {
        super.visitAnnotation(annotation);
        if (!PsiUtil.getLanguageLevel(annotation).isAtLeast(LanguageLevel.JDK_1_7)) return;
        if (Comparing.strEqual(annotation.getQualifiedName(), SuppressWarnings.class.getName())) {
          final PsiAnnotationOwner owner = annotation.getOwner();
          if (owner instanceof PsiModifierList) {
            final PsiModifierListOwner modifierListOwner = PsiTreeUtil.getParentOfType(((PsiModifierList)owner), PsiModifierListOwner.class);
            if (modifierListOwner != null) {
              final Collection<String> suppressedIds =
                SuppressManager.getInstance().getInspectionIdsSuppressedInAnnotation(modifierListOwner);
              if (suppressedIds.contains(RemoveUncheckedWarningFix.UNCHECKED)) {
                checkIfSafeToRemoveWarning(annotation, modifierListOwner, holder);
              }
            }
          }
        }
      }
    };
  }

  private static void checkIfSafeToRemoveWarning(PsiElement suppressElement, PsiElement placeToCheckWarningsIn, ProblemsHolder holder) {
    final HashSet<PsiElement> warningsElements = new HashSet<PsiElement>();
    collectUncheckedWarnings(placeToCheckWarningsIn, warningsElements);
    if (warningsElements.isEmpty()) {
      final int uncheckedIdx = suppressElement.getText().indexOf(RemoveUncheckedWarningFix.UNCHECKED);
      holder.registerProblem(suppressElement,
                             uncheckedIdx > -1 ? new TextRange(uncheckedIdx, uncheckedIdx + RemoveUncheckedWarningFix.UNCHECKED.length()) : null,
                             "Redundant suppression", new RemoveUncheckedWarningFix());
    }
  }

  public static void collectUncheckedWarnings(final PsiElement place, final Collection<PsiElement> warningsElements) {
    final UncheckedWarningLocalInspection.UncheckedWarningsVisitor visitor =
      new UncheckedWarningLocalInspection.UncheckedWarningsVisitor(false) {
        @Override
        protected void registerProblem(String message, PsiElement psiElement, LocalQuickFix... quickFix) {
          warningsElements.add(psiElement);
        }
      };
    final PossibleHeapPollutionVarargsInspection.HeapPollutionVisitor hVisitor = new PossibleHeapPollutionVarargsInspection.HeapPollutionVisitor() {
      @Override
      protected void registerProblem(PsiMethod method, PsiIdentifier nameIdentifier) {
        warningsElements.add(method);
      }
    };

    place.accept(new JavaRecursiveElementVisitor(){
      @Override
      public void visitElement(PsiElement element) {
        super.visitElement(element);
        element.accept(visitor);
        element.accept(hVisitor);
      }
    });
  }

  private static boolean collectCommentSuppresses(PsiElement psiElement, Set<String> tools2Suppress) {
      final Matcher matcher = SuppressionUtil.SUPPRESS_IN_LINE_COMMENT_PATTERN.matcher(psiElement.getText());
      if (matcher.matches()) {
        final String inspections = matcher.group(1);
        final String[] toolsIds = inspections.split("[, ]");
        for (String toolsId : toolsIds) {
          final String id = toolsId.trim();
          if (!id.isEmpty()) {
            tools2Suppress.add(id);
          }
        }
        return true;
      }
      return false;
    }

  private static class RemoveUncheckedWarningFix implements LocalQuickFix {
    @NonNls private static final String UNCHECKED = "unchecked";

    @NotNull
    @Override
    public String getName() {
      return "Remove redundant \"unchecked\" suppression";
    }

    @NotNull
    @Override
    public String getFamilyName() {
      return getName();
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      final PsiElement psiElement = descriptor.getPsiElement();
      if (psiElement instanceof PsiComment) {
        final Set<String> tools2Suppress = new LinkedHashSet<String>();
        if (collectCommentSuppresses(psiElement, tools2Suppress)) {
          tools2Suppress.remove(UNCHECKED);
          if (tools2Suppress.isEmpty()) {
            psiElement.delete();
          } else {
            psiElement.replace(JavaPsiFacade.getElementFactory(project).createCommentFromText("//" + SuppressionUtil.SUPPRESS_INSPECTIONS_TAG_NAME + " " + StringUtil
              .join(tools2Suppress, ", "), psiElement));
          }
        }
      } else if (psiElement instanceof PsiAnnotation) {
        final PsiAnnotation annotation = (PsiAnnotation)psiElement;
        final PsiModifierList owner = (PsiModifierList)annotation.getOwner();
        final PsiModifierListOwner listOwner = PsiTreeUtil.getParentOfType(owner, PsiModifierListOwner.class);
        if (listOwner != null) {
          final Collection<String> tool2Suppress = SuppressManager.getInstance().getInspectionIdsSuppressedInAnnotation(listOwner);
          tool2Suppress.remove(UNCHECKED);
          if (tool2Suppress.isEmpty()) {
            annotation.delete();
          } else {
            annotation.replace(JavaPsiFacade.getElementFactory(project).createAnnotationFromText("@" + annotation.getQualifiedName() + "({" +StringUtil.join(tool2Suppress, new Function<String, String>() {
              @Override
              public String fun(String s) {
                return "\"" + s + "\"";
              }
            }, ", ")+ "})", annotation));
          }
        }
      }
    }
  }
}
