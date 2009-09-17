package com.intellij.codeInspection;

import com.intellij.analysis.AnalysisScope;
import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInsight.daemon.impl.AnnotationHolderImpl;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.lang.ASTNode;
import com.intellij.lang.LanguageAnnotators;
import com.intellij.lang.annotation.Annotation;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.psi.PsiRecursiveElementVisitor;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class AnnotatorBasedInspection extends GlobalInspectionTool {
  @Override
  public boolean isGraphNeeded() {
    return false;
  }

  @NotNull
  @Override
  public HighlightDisplayLevel getDefaultLevel() {
    return HighlightDisplayLevel.ERROR;
  }

  @Override
  public void runInspection(AnalysisScope scope,
                            final InspectionManager manager,
                            final GlobalInspectionContext globalContext,
                            final ProblemDescriptionsProcessor problemDescriptionsProcessor) {
    scope.accept(new MyPsiRecursiveElementVisitor(manager, globalContext, problemDescriptionsProcessor));
  }

  @Nls
  @NotNull
  @Override
  public String getGroupDisplayName() {
    return "General";
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
    private AnnotationHolder myHolder;
    private List<Annotator> annotators;
    private PsiFile myFile;

    public MyPsiRecursiveElementVisitor(final InspectionManager manager, final GlobalInspectionContext globalContext,
                                        final ProblemDescriptionsProcessor problemDescriptionsProcessor) {
      myHolder = new AnnotationHolderImpl() {
        @Override
        public Annotation createErrorAnnotation(@NotNull PsiElement elt, String message) {
          return createProblem(elt, message, ProblemHighlightType.ERROR, HighlightSeverity.ERROR, null);
        }

        @Override
        public Annotation createWarningAnnotation(PsiElement elt, String message) {
          return createProblem(elt, message, ProblemHighlightType.GENERIC_ERROR_OR_WARNING, HighlightSeverity.WARNING, null);
        }

        @Override
        public Annotation createInfoAnnotation(PsiElement elt, String message) {
          return super.createInfoAnnotation(elt, message);
        }

        @Override
        public Annotation createInformationAnnotation(PsiElement elt, String message) {
          return createProblem(elt, message, ProblemHighlightType.INFORMATION, HighlightSeverity.INFORMATION, null);
        }

        private Annotation createProblem(PsiElement elt, String message, ProblemHighlightType problemHighlightType,
                                         HighlightSeverity severity, TextRange range) {
          GlobalInspectionUtil.createProblem(elt, message, problemHighlightType, range, manager, problemDescriptionsProcessor, globalContext);
          return super.createAnnotation(elt.getTextRange(), severity, message);
        }

        @Override
        public Annotation createErrorAnnotation(ASTNode node, String message) {
          return createErrorAnnotation(node.getPsi(), message);
        }

        @Override
        public Annotation createWarningAnnotation(ASTNode node, String message) {
          return createWarningAnnotation(node.getPsi(), message);
        }

        @Override
        public Annotation createInformationAnnotation(ASTNode node, String message) {
          return createInformationAnnotation(node.getPsi(), message);
        }

        @Override
        public Annotation createInfoAnnotation(ASTNode node, String message) {
          return createInfoAnnotation(node.getPsi(), message);
        }

        @Override
        protected Annotation createAnnotation(TextRange range, HighlightSeverity severity, String message) {
          if (severity != HighlightSeverity.INFORMATION) {
            GlobalInspectionUtil.createProblem(
              myFile,
              message,
              HighlightInfo.convertSeverityToProblemHighlight(severity),
              range,
              manager,
              problemDescriptionsProcessor,
              globalContext
            );
          }
          return super.createAnnotation(range, severity, message);
        }
      };
    }

    @Override
    public void visitFile(PsiFile file) {
      myFile = file;
      super.visitFile(file);
    }

    @Override
    public void visitElement(PsiElement element) {
      super.visitElement(element);

      List<Annotator> annotators = this.annotators != null ?
        this.annotators:LanguageAnnotators.INSTANCE.allForLanguage(element.getLanguage());
      if (!annotators.isEmpty()) {
        for(Annotator annotator:annotators) {
          annotator.annotate(element, myHolder);
        }
      }
      if (element instanceof PsiLanguageInjectionHost) {
        ((PsiLanguageInjectionHost)element).processInjectedPsi(this);
      }
    }

    public void visit(@NotNull PsiFile injectedPsi, @NotNull List<PsiLanguageInjectionHost.Shred> places) {
      try {
        annotators = LanguageAnnotators.INSTANCE.allForLanguage(injectedPsi.getLanguage());
        injectedPsi.acceptChildren(this);
      } finally {
        annotators = null;
      }
    }
  }
}
