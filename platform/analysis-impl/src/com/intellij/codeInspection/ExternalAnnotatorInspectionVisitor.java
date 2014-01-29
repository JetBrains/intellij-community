/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
  private final boolean myOnTheFly;

  public ExternalAnnotatorInspectionVisitor(ProblemsHolder holder, ExternalAnnotator annotator, boolean onTheFly) {
    myHolder = holder;
    myAnnotator = annotator;
    myOnTheFly = onTheFly;
  }

  @NotNull
  public static <Init,Result> ProblemDescriptor[] checkFileWithExternalAnnotator(@NotNull PsiFile file,
                                                                                 @NotNull InspectionManager manager,
                                                                                 boolean isOnTheFly,
                                                                                 @NotNull ExternalAnnotator<Init,Result> annotator) {
    if (isOnTheFly) {
      // concrete JSLinterExternalAnnotator implementation does this work
      return ProblemDescriptor.EMPTY_ARRAY;
    }

    Init info = annotator.collectInformation(file);
    if (info != null) {
      Result annotationResult = annotator.doAnnotate(info);
      if (annotationResult == null) {
        return ProblemDescriptor.EMPTY_ARRAY;
      }
      AnnotationHolderImpl annotationHolder = new AnnotationHolderImpl(new AnnotationSession(file));
      annotator.apply(file, annotationResult, annotationHolder);
      return convertToProblemDescriptors(annotationHolder, manager, file);
    }
    return ProblemDescriptor.EMPTY_ARRAY;
  }

  @NotNull
  private static ProblemDescriptor[] convertToProblemDescriptors(@NotNull final List<Annotation> annotations,
                                                                 @NotNull final InspectionManager manager,
                                                                 @NotNull final PsiFile file) {
    if (annotations.isEmpty()) {
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
