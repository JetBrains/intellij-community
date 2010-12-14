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

package com.intellij.codeInspection;

import com.intellij.analysis.AnalysisScope;
import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInsight.daemon.impl.AnnotationHolderImpl;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInspection.ex.JobDescriptor;
import com.intellij.lang.ASTNode;
import com.intellij.lang.LanguageAnnotators;
import com.intellij.lang.annotation.*;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.psi.PsiRecursiveElementVisitor;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class AnnotatorBasedInspection extends GlobalInspectionTool {
  private static final JobDescriptor ANNOTATOR = new JobDescriptor(InspectionsBundle.message("inspection.processing.job.descriptor2"));

  @Override
  public boolean isGraphNeeded() {
    return false;
  }

  @Override
  public JobDescriptor[] getAdditionalJobs() {
    return new JobDescriptor[]{ANNOTATOR};
  }

  @NotNull
  @Override
  public HighlightDisplayLevel getDefaultLevel() {
    return HighlightDisplayLevel.ERROR;
  }

  @Override
  public void runInspection(AnalysisScope scope,
                            InspectionManager manager,
                            GlobalInspectionContext globalContext,
                            ProblemDescriptionsProcessor problemDescriptionsProcessor) {
    ANNOTATOR.setTotalAmount(scope.getFileCount());
    scope.accept(new MyPsiRecursiveElementVisitor(manager, globalContext, problemDescriptionsProcessor));
  }

  @Nls
  @NotNull
  @Override
  public String getGroupDisplayName() {
    return GENERAL_GROUP_NAME;
  }

  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return "Annotator";
  }

  @NotNull
  @Override
  public String getShortName() {
    return "Annotator";
  }

  private static class MyPsiRecursiveElementVisitor extends PsiRecursiveElementVisitor implements PsiLanguageInjectionHost.InjectedPsiVisitor {
    private AnnotationHolderImpl myHolder;
    private List<Annotator> annotators;
    private PsiFile myFile;
    private final InspectionManager myManager;
    private final GlobalInspectionContext myGlobalContext;
    private final ProblemDescriptionsProcessor myProblemDescriptionsProcessor;

    public MyPsiRecursiveElementVisitor(final InspectionManager manager,
                                        final GlobalInspectionContext globalContext,
                                        final ProblemDescriptionsProcessor problemDescriptionsProcessor) {
      myManager = manager;
      myGlobalContext = globalContext;
      myProblemDescriptionsProcessor = problemDescriptionsProcessor;
    }

    @Override
    public void visitFile(PsiFile file) {
      myFile = file;
      final VirtualFile virtualFile = myFile.getVirtualFile();
      if (virtualFile != null) {
        myGlobalContext.incrementJobDoneAmount(ANNOTATOR, ProjectUtil.calcRelativeToProjectPath(virtualFile, myFile.getProject()));
      }
      myHolder = new AnnotationHolderImpl(new AnnotationSession(file)) {
        @Override
        public Annotation createErrorAnnotation(@NotNull PsiElement elt, String message) {
          return createProblem(elt, message, ProblemHighlightType.ERROR, HighlightSeverity.ERROR, null);
        }

        @Override
        public Annotation createWarningAnnotation(@NotNull PsiElement elt, String message) {
          return createProblem(elt, message, ProblemHighlightType.GENERIC_ERROR_OR_WARNING, HighlightSeverity.WARNING, null);
        }

        @Override
        public Annotation createInfoAnnotation(@NotNull PsiElement elt, String message) {
          return super.createInfoAnnotation(elt, message);
        }

        @Override
        public Annotation createInformationAnnotation(@NotNull PsiElement elt, String message) {
          return createProblem(elt, message, ProblemHighlightType.INFORMATION, HighlightSeverity.INFORMATION, null);
        }

        private Annotation createProblem(PsiElement elt,
                                         String message,
                                         ProblemHighlightType problemHighlightType,
                                         HighlightSeverity severity,
                                         TextRange range) {
          GlobalInspectionUtil
            .createProblem(elt, message, problemHighlightType, range, myManager, myProblemDescriptionsProcessor, myGlobalContext);
          return super.createAnnotation(elt.getTextRange(), severity, message);
        }

        @Override
        public Annotation createErrorAnnotation(@NotNull ASTNode node, String message) {
          return createErrorAnnotation(node.getPsi(), message);
        }

        @Override
        public Annotation createWarningAnnotation(@NotNull ASTNode node, String message) {
          return createWarningAnnotation(node.getPsi(), message);
        }

        @Override
        public Annotation createInformationAnnotation(@NotNull ASTNode node, String message) {
          return createInformationAnnotation(node.getPsi(), message);
        }

        @Override
        public Annotation createInfoAnnotation(@NotNull ASTNode node, String message) {
          return createInfoAnnotation(node.getPsi(), message);
        }

        @Override
        protected Annotation createAnnotation(TextRange range, HighlightSeverity severity, String message) {
          if (severity != HighlightSeverity.INFORMATION) {
            GlobalInspectionUtil.createProblem(myFile, message, HighlightInfo.convertSeverityToProblemHighlight(severity), range, myManager,
                                               myProblemDescriptionsProcessor, myGlobalContext);
          }
          return super.createAnnotation(range, severity, message);
        }
      };
      super.visitFile(file);
    }

    @Override
    public void visitElement(PsiElement element) {
      super.visitElement(element);

      List<Annotator> elemAnnos = annotators != null ? annotators : LanguageAnnotators.INSTANCE.allForLanguage(element.getLanguage());
      for (Annotator annotator : elemAnnos) {
        annotator.annotate(element, myHolder);
      }
      if (element instanceof PsiLanguageInjectionHost) {
        ((PsiLanguageInjectionHost)element).processInjectedPsi(this);
      }
    }

    public void visit(@NotNull PsiFile injectedPsi, @NotNull List<PsiLanguageInjectionHost.Shred> places) {
      try {
        annotators = LanguageAnnotators.INSTANCE.allForLanguage(injectedPsi.getLanguage());
        injectedPsi.acceptChildren(this);
      }
      finally {
        annotators = null;
      }
    }
  }
}
