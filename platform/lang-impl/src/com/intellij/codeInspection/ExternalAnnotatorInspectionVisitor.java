package com.intellij.codeInspection;

import com.intellij.codeInsight.daemon.impl.AnnotationHolderImpl;
import com.intellij.lang.annotation.Annotation;
import com.intellij.lang.annotation.AnnotationSession;
import com.intellij.lang.annotation.ExternalAnnotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class ExternalAnnotatorInspectionVisitor extends PsiElementVisitor {
  private static final Logger LOG = Logger.getInstance(ExternalAnnotatorInspectionVisitor.class);

  private final ProblemsHolder myHolder;
  private final ExternalAnnotator myAnnotator;
  private boolean myOnTheFly;

  public ExternalAnnotatorInspectionVisitor(ProblemsHolder holder, ExternalAnnotator annotator, boolean onTheFly) {
    myHolder = holder;
    myAnnotator = annotator;
    myOnTheFly = onTheFly;
  }

  public static ProblemDescriptor[] checkFileWithExternalAnnotator(PsiFile file,
                                                                   InspectionManager manager,
                                                                   boolean isOnTheFly, ExternalAnnotator annotator) {
    if (isOnTheFly) {
      // concrete JSLinterExternalAnnotator implementation does this work
      return ProblemDescriptor.EMPTY_ARRAY;
    }

    Object info = annotator.collectInformation(file);
    if (info != null) {
      Object annotationResult = annotator.doAnnotate(info);
      if (annotationResult == null) {
        return ProblemDescriptor.EMPTY_ARRAY;
      }
      AnnotationHolderImpl annotationHolder = new AnnotationHolderImpl(new AnnotationSession(file));
      annotator.apply(file, annotationResult, annotationHolder);
      return convertToProblemDescriptors(annotationHolder, manager, file);
    }
    return ProblemDescriptor.EMPTY_ARRAY;
  }

  private static ProblemDescriptor[] convertToProblemDescriptors(@NotNull final List<Annotation> annotations,
                                                                 @NotNull final InspectionManager manager,
                                                                 @NotNull final PsiFile file) {
    if (annotations.size() == 0) {
      return ProblemDescriptor.EMPTY_ARRAY;
    }

    final List<ProblemDescriptor> problems = new ArrayList<ProblemDescriptor>();
    for (final Annotation annotation : annotations) {
      if (annotation.getSeverity() == HighlightSeverity.INFORMATION ||
          annotation.getStartOffset() == annotation.getEndOffset()) {
        continue;
      }

      final PsiElement startElement = file.findElementAt(annotation.getStartOffset());
      final PsiElement endElement = file.findElementAt(annotation.getEndOffset() - 1);
      if (startElement == null || endElement == null) {
        continue;
      }

      problems.add(manager.createProblemDescriptor(startElement, endElement, annotation.getMessage(),
                                                   ProblemHighlightType.GENERIC_ERROR_OR_WARNING, false));
    }
    return problems.toArray(new ProblemDescriptor[problems.size()]);
  }

  @Override
  public void visitFile(PsiFile file) {
    if (!myOnTheFly) {
      ProblemDescriptor[] descriptors = checkFileWithExternalAnnotator(file, myHolder.getManager(),
                                                                       false, myAnnotator);
      addDescriptors(descriptors);
    }
  }

  private void addDescriptors(@NotNull ProblemDescriptor[] descriptors) {
    for (ProblemDescriptor descriptor : descriptors) {
      LOG.assertTrue(descriptor != null, getClass().getName());
      myHolder.registerProblem(descriptor);
    }
  }
}
