// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection;

import com.intellij.codeInsight.daemon.impl.AnnotationHolderImpl;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.codeInsight.intention.preview.IntentionPreviewUtils;
import com.intellij.lang.annotation.AnnotationSession;
import com.intellij.lang.annotation.ExternalAnnotator;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Iconable;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Objects;

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
  public void visitFile(@NotNull PsiFile file) {
    ProblemDescriptor[] descriptors = checkFileWithExternalAnnotator(file, myHolder.getManager(), myIsOnTheFly, myAnnotator);
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
      return ReadAction.compute(() -> {
        AnnotationHolderImpl annotationHolder = new AnnotationHolderImpl(new AnnotationSession(file), true);
        annotationHolder.applyExternalAnnotatorWithContext(file, annotator, annotationResult);
        annotationHolder.assertAllAnnotationsCreated();
        return ProblemDescriptorUtil.convertToProblemDescriptors(annotationHolder, file);
      });
    }
    return ProblemDescriptor.EMPTY_ARRAY;
  }


  private void addDescriptors(ProblemDescriptor @NotNull [] descriptors) {
    for (ProblemDescriptor descriptor : descriptors) {
      LOG.assertTrue(descriptor != null, getClass().getName());
      myHolder.registerProblem(descriptor);
    }
  }

  public static class LocalQuickFixBackedByIntentionAction implements LocalQuickFix, Iconable {
    private final IntentionAction myAction;

    public LocalQuickFixBackedByIntentionAction(@NotNull IntentionAction action) {
      myAction = action;
    }

    @NotNull
    @Override
    public String getName() {
      return myAction.getText();
    }

    @NotNull
    @Override
    public String getFamilyName() {
      return myAction.getFamilyName();
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      myAction.invoke(project, null, getPsiFile(descriptor));
    }

    @Override
    public @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project, @NotNull ProblemDescriptor previewDescriptor) {
      return myAction.generatePreview(project,
                                      Objects.requireNonNull(IntentionPreviewUtils.getPreviewEditor()),
                                      Objects.requireNonNull(getPsiFile(previewDescriptor)));
    }

    @Nullable
    @Override
    public PsiElement getElementToMakeWritable(@NotNull PsiFile file) {
      return myAction.getElementToMakeWritable(file);
    }

    @Nullable
    private static PsiFile getPsiFile(@NotNull ProblemDescriptor descriptor) {
      PsiElement startElement = descriptor.getStartElement();
      if (startElement != null) {
        return startElement.getContainingFile();
      }
      PsiElement endElement = descriptor.getEndElement();
      if (endElement != null) {
        return endElement.getContainingFile();
      }
      return null;
    }

    @Override
    public Icon getIcon(@IconFlags int flags) {
      if (myAction instanceof Iconable) {
        return ((Iconable) myAction).getIcon(flags);
      }
      return null;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      LocalQuickFixBackedByIntentionAction action = (LocalQuickFixBackedByIntentionAction)o;

      return myAction.equals(action.myAction);
    }

    @Override
    public int hashCode() {
      return myAction.hashCode();
    }
  }
}
