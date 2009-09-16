package com.intellij.codeInspection;

import com.intellij.analysis.AnalysisScope;
import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.lang.LanguageAnnotators;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotation;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.psi.PsiRecursiveElementVisitor;
import com.intellij.codeInsight.daemon.impl.AnnotationHolderImpl;
import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author yole
 */
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
    private final InspectionManager manager;
    private final GlobalInspectionContext globalContext;
    private final ProblemDescriptionsProcessor problemDescriptionsProcessor;
    private AnnotationHolder myHolder;
    private List<Annotator> annotators;

    public MyPsiRecursiveElementVisitor(final InspectionManager manager, final GlobalInspectionContext globalContext,
                                        final ProblemDescriptionsProcessor problemDescriptionsProcessor) {
      this.manager = manager;
      this.globalContext = globalContext;
      this.problemDescriptionsProcessor = problemDescriptionsProcessor;
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
          return createProblem(elt, message, ProblemHighlightType.INFO, HighlightSeverity.INFO, null);
        }

        @Override
        public Annotation createInformationAnnotation(PsiElement elt, String message) {
          return createProblem(elt, message, ProblemHighlightType.INFORMATION, HighlightSeverity.INFORMATION, null);
        }

        private Annotation createProblem(PsiElement elt, String message, ProblemHighlightType problemHighlightType,
                                         HighlightSeverity severity, TextRange range) {
          ProblemDescriptor descriptor = manager.createProblemDescriptor(
              elt,
              range,
              GlobalInspectionUtil.createInspectionMessage(message),
              problemHighlightType
          );
          problemDescriptionsProcessor.addProblemElement(
            GlobalInspectionUtil.retrieveRefElement(elt, globalContext),
            descriptor
          );
          return super.createAnnotation(elt.getTextRange(), severity, message);
        }

        @Override
        protected Annotation createAnnotation(TextRange range, HighlightSeverity severity, String message) {
          assert false;
          return super.createAnnotation(range, severity, message);
        }
      };
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
