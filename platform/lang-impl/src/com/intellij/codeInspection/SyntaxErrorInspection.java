package com.intellij.codeInspection;

import com.intellij.analysis.AnalysisScope;
import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.psi.*;
import com.intellij.codeInsight.highlighting.HighlightErrorFilter;
import com.intellij.codeInsight.daemon.impl.analysis.DefaultHighlightVisitor;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author yole
 */
public class SyntaxErrorInspection extends GlobalInspectionTool {
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
    return "Syntax error";
  }

  @NotNull
  @Override
  public String getShortName() {
    return "SyntaxError";
  }

  private static class MyPsiRecursiveElementVisitor extends PsiRecursiveElementVisitor implements PsiLanguageInjectionHost.InjectedPsiVisitor {
    private final InspectionManager manager;
    private final GlobalInspectionContext globalContext;
    private final ProblemDescriptionsProcessor problemDescriptionsProcessor;
    private final HighlightErrorFilter[] errorFilters;

    public MyPsiRecursiveElementVisitor(InspectionManager manager, GlobalInspectionContext globalContext,
                                        ProblemDescriptionsProcessor problemDescriptionsProcessor) {
      this.manager = manager;
      this.globalContext = globalContext;
      this.problemDescriptionsProcessor = problemDescriptionsProcessor;
      this.errorFilters = Extensions.getExtensions(DefaultHighlightVisitor.FILTER_EP_NAME, manager.getProject());
    }

    @Override
    public void visitElement(PsiElement element) {
      super.visitElement(element);
      if (element instanceof PsiLanguageInjectionHost) {
        ((PsiLanguageInjectionHost)element).processInjectedPsi(this);
      }
    }

    @Override
    public void visitErrorElement(PsiErrorElement element) {
      super.visitErrorElement(element);
      for (final HighlightErrorFilter errorFilter : errorFilters) {
        if (!errorFilter.shouldHighlightErrorElement(element)) return;
      }

      CommonProblemDescriptor descriptor;
      final TextRange textRange = element.getTextRange();
      if (textRange.getLength() > 0) {
        descriptor = manager.createProblemDescriptor(
          element,
          GlobalInspectionUtil.createInspectionMessage(element.getErrorDescription()),
          ProblemHighlightType.ERROR,
          null
        );
      }
      else {
        PsiElement parent = element;
        while(true) {
          parent = parent.getParent();
          if (parent == null) break;
          TextRange r = parent.getTextRange();
          if (r == null) return; // no place to attach the problem descriptor to
          if (r.getLength() > 0) {
            break;
          }
        }
        if (parent == null) return;
        int offset = element.getTextRange().getStartOffset() - parent.getTextRange().getStartOffset();
        descriptor = manager.createProblemDescriptor(parent,
                                                     new TextRange(offset, offset+1),
                                                     GlobalInspectionUtil.createInspectionMessage(element.getErrorDescription()),
                                                     ProblemHighlightType.ERROR);
      }

      problemDescriptionsProcessor.addProblemElement(GlobalInspectionUtil.retrieveRefElement(element, globalContext), descriptor);
    }

    public void visit(@NotNull PsiFile injectedPsi, @NotNull List<PsiLanguageInjectionHost.Shred> places) {
      injectedPsi.acceptChildren(this);
    }
  }
}
