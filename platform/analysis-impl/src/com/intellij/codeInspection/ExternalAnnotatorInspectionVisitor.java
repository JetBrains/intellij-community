// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection;

import com.intellij.codeInsight.daemon.impl.AnnotationHolderImpl;
import com.intellij.codeInsight.daemon.impl.AnnotationSessionImpl;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.lang.annotation.Annotation;
import com.intellij.lang.annotation.ExternalAnnotator;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class ExternalAnnotatorInspectionVisitor extends PsiElementVisitor {
  private static final Logger LOG = Logger.getInstance(ExternalAnnotatorInspectionVisitor.class);

  private final ProblemsHolder myHolder;
  private final ExternalAnnotator<?, ?> myAnnotator;
  private final boolean myIsOnTheFly;

  public ExternalAnnotatorInspectionVisitor(ProblemsHolder holder, ExternalAnnotator<?, ?> annotator, boolean isOnTheFly) {
    myHolder = holder;
    myAnnotator = annotator;
    myIsOnTheFly = isOnTheFly;
  }

  @Override
  public void visitFile(@NotNull PsiFile psiFile) {
    ProblemDescriptor[] descriptors = checkFileWithExternalAnnotator(psiFile, myHolder.getManager(), myIsOnTheFly, myAnnotator);
    addDescriptors(descriptors);
  }

  public static <Init,Result> ProblemDescriptor @NotNull [] checkFileWithExternalAnnotator(@NotNull PsiFile file,
                                                                                           @NotNull InspectionManager manager,
                                                                                           boolean isOnTheFly,
                                                                                           @NotNull ExternalAnnotator<Init, Result> annotator) {
    if (isOnTheFly) {
      // ExternalAnnotator does this work
      return ProblemDescriptor.EMPTY_ARRAY;
    }

    Init info = ReadAction.compute(() -> annotator.collectInformation(file));
    if (info != null) {
      Result annotationResult = annotator.doAnnotate(info);
      if (annotationResult == null) {
        return ProblemDescriptor.EMPTY_ARRAY;
      }
      return ReadAction.compute(() -> AnnotationSessionImpl.computeWithSession(file, true, annotator, annotationHolder -> {
        ((AnnotationHolderImpl)annotationHolder).applyExternalAnnotatorWithContext(file, annotationResult);
        ((AnnotationHolderImpl)annotationHolder).assertAllAnnotationsCreated();
        return ProblemDescriptorUtil.convertToProblemDescriptors((List<? extends Annotation>)annotationHolder, file);
      }));
    }
    return ProblemDescriptor.EMPTY_ARRAY;
  }


  private void addDescriptors(ProblemDescriptor @NotNull [] descriptors) {
    for (ProblemDescriptor descriptor : descriptors) {
      LOG.assertTrue(descriptor != null, getClass().getName());
      myHolder.registerProblem(descriptor);
    }
  }

  /**
   * @deprecated use {@link com.intellij.codeInspection.LocalQuickFixBackedByIntentionAction} instead. 
   */
  @Deprecated
  public static class LocalQuickFixBackedByIntentionAction extends com.intellij.codeInspection.LocalQuickFixBackedByIntentionAction {
    public LocalQuickFixBackedByIntentionAction(@NotNull IntentionAction action) {
      super(action);
    }
  }
}
