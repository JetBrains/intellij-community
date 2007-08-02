/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.util.xml.highlighting;

import com.intellij.codeInsight.daemon.impl.AnnotationHolderImpl;
import com.intellij.codeInsight.daemon.impl.SeverityRegistrar;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.lang.annotation.Annotation;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.editor.colors.CodeInsightColors;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.util.Function;
import org.jetbrains.annotations.Nullable;

/**
 * User: Sergey.Vasiliev
 */
public class DomElementsHighlightingUtil {

  private static final AnnotationHolderImpl EMPTY_ANNOTATION_HOLDER = new AnnotationHolderImpl() {
    public boolean add(final Annotation annotation) {
      return false;
    }
  };

  private DomElementsHighlightingUtil() {
  }

  @Nullable
  public static ProblemDescriptor createProblemDescriptors(final InspectionManager manager,
                                                                 final DomElementProblemDescriptor problemDescriptor) {
    final ProblemHighlightType type = getProblemHighlightType(problemDescriptor);
    return createProblemDescriptors(problemDescriptor, new Function<Pair<TextRange, PsiElement>, ProblemDescriptor>() {
      public ProblemDescriptor fun(final Pair<TextRange, PsiElement> s) {
        return manager
          .createProblemDescriptor(s.second, s.first, problemDescriptor.getDescriptionTemplate(), type, problemDescriptor.getFixes());
      }
    });
  }

  private static ProblemHighlightType getProblemHighlightType(final DomElementProblemDescriptor problemDescriptor) {
    if (problemDescriptor instanceof DomElementResolveProblemDescriptor) {
      final TextRange range = ((DomElementResolveProblemDescriptor)problemDescriptor).getPsiReference().getRangeInElement();
      if (range.getStartOffset() != range.getEndOffset()) {
        return ProblemHighlightType.LIKE_UNKNOWN_SYMBOL;
      }
    }
    return ProblemHighlightType.GENERIC_ERROR_OR_WARNING;
  }

  @Nullable
  public static Annotation createAnnotation(final DomElementProblemDescriptor problemDescriptor) {

    return createProblemDescriptors(problemDescriptor, new Function<Pair<TextRange, PsiElement>, Annotation>() {
      public Annotation fun(final Pair<TextRange, PsiElement> s) {
        String text = problemDescriptor.getDescriptionTemplate();
        if (StringUtil.isEmpty(text)) text = null;
        final HighlightSeverity severity = problemDescriptor.getHighlightSeverity();
        final AnnotationHolderImpl holder = EMPTY_ANNOTATION_HOLDER;

        TextRange range = s.first;
        if (text == null) range = TextRange.from(range.getStartOffset(), 0);
        range = range.shiftRight(s.second.getTextRange().getStartOffset());
        final Annotation annotation = createAnnotation(severity, holder, range, text, s.second.getProject());

        if (problemDescriptor instanceof DomElementResolveProblemDescriptor) {
          annotation.setTextAttributes(CodeInsightColors.WRONG_REFERENCES_ATTRIBUTES);
        }

        for(LocalQuickFix fix:problemDescriptor.getFixes()) {
          if (fix instanceof IntentionAction) annotation.registerFix((IntentionAction)fix);
        }
        return annotation;
      }
    });
  }

  private static Annotation createAnnotation(final HighlightSeverity severity,
                                             final AnnotationHolderImpl holder,
                                             final TextRange range,
                                             final String text, final Project project) {
    if (SeverityRegistrar.getInstance(project).compare(severity, HighlightSeverity.ERROR) >= 0) return holder.createErrorAnnotation(range, text);
    if (SeverityRegistrar.getInstance(project).compare(severity, HighlightSeverity.WARNING) >= 0) return holder.createWarningAnnotation(range, text);
    if (SeverityRegistrar.getInstance(project).compare(severity, HighlightSeverity.INFO) >= 0) return holder.createInformationAnnotation(range, text);
    return holder.createInfoAnnotation(range, text);
  }

  @Nullable
  private static <T> T createProblemDescriptors(final DomElementProblemDescriptor problemDescriptor,
                                                      final Function<Pair<TextRange, PsiElement>, T> creator) {

    final Pair<TextRange, PsiElement> range = ((DomElementProblemDescriptorImpl)problemDescriptor).getProblemRange();
    return range == DomElementProblemDescriptorImpl.NO_PROBLEM ? null : creator.fun(range);
  }

}
