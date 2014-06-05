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
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.lang.annotation.Annotation;
import com.intellij.lang.annotation.AnnotationSession;
import com.intellij.lang.annotation.ExternalAnnotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Iconable;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiFile;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.IdentityHashMap;
import java.util.List;

public class ExternalAnnotatorInspectionVisitor extends PsiElementVisitor {
  private static final Logger LOG = Logger.getInstance(ExternalAnnotatorInspectionVisitor.class);

  private final ProblemsHolder myHolder;
  private final ExternalAnnotator myAnnotator;
  private final boolean myIsOnTheFly;

  public ExternalAnnotatorInspectionVisitor(ProblemsHolder holder, ExternalAnnotator annotator, boolean isOnTheFly) {
    myHolder = holder;
    myAnnotator = annotator;
    myIsOnTheFly = isOnTheFly;
  }

  @Override
  public void visitFile(PsiFile file) {
    ProblemDescriptor[] descriptors = checkFileWithExternalAnnotator(file, myHolder.getManager(), myIsOnTheFly, myAnnotator);
    addDescriptors(descriptors);
  }

  @NotNull
  public static <Init,Result> ProblemDescriptor[] checkFileWithExternalAnnotator(@NotNull PsiFile file,
                                                                                 @NotNull InspectionManager manager,
                                                                                 boolean isOnTheFly,
                                                                                 @NotNull ExternalAnnotator<Init,Result> annotator) {
    if (isOnTheFly) {
      // ExternalAnnotator does this work
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

    List<ProblemDescriptor> problems = ContainerUtil.newArrayListWithCapacity(annotations.size());
    IdentityHashMap<IntentionAction, LocalQuickFix> quickFixMappingCache = ContainerUtil.newIdentityHashMap();
    for (Annotation annotation : annotations) {
      if (annotation.getSeverity() == HighlightSeverity.INFORMATION ||
          annotation.getStartOffset() == annotation.getEndOffset()) {
        continue;
      }

      final PsiElement startElement = file.findElementAt(annotation.getStartOffset());
      final PsiElement endElement = file.findElementAt(annotation.getEndOffset() - 1);
      if (startElement == null || endElement == null) {
        continue;
      }

      LocalQuickFix[] quickFixes = toLocalQuickFixes(annotation.getQuickFixes(), quickFixMappingCache);
      ProblemDescriptor descriptor = manager.createProblemDescriptor(startElement,
                                                                     endElement,
                                                                     annotation.getMessage(),
                                                                     ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                                                                     false,
                                                                     quickFixes);
      problems.add(descriptor);
    }
    return problems.toArray(new ProblemDescriptor[problems.size()]);
  }

  @NotNull
  private static LocalQuickFix[] toLocalQuickFixes(@Nullable List<Annotation.QuickFixInfo> fixInfos,
                                                   @NotNull IdentityHashMap<IntentionAction, LocalQuickFix> quickFixMappingCache) {
    if (fixInfos == null || fixInfos.isEmpty()) {
      return LocalQuickFix.EMPTY_ARRAY;
    }
    LocalQuickFix[] result = new LocalQuickFix[fixInfos.size()];
    int i = 0;
    for (Annotation.QuickFixInfo fixInfo : fixInfos) {
      IntentionAction intentionAction = fixInfo.quickFix;
      final LocalQuickFix fix;
      if (intentionAction instanceof LocalQuickFix) {
        fix = (LocalQuickFix) intentionAction;
      }
      else {
        LocalQuickFix lqf = quickFixMappingCache.get(intentionAction);
        if (lqf == null) {
          lqf = new LocalQuickFixBackedByIntentionAction(intentionAction);
          quickFixMappingCache.put(intentionAction, lqf);
        }
        fix = lqf;
      }
      result[i++] = fix;
    }
    return result;
  }

  private void addDescriptors(@NotNull ProblemDescriptor[] descriptors) {
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
