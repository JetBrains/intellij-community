package com.intellij.codeInspection;

import com.intellij.analysis.AnalysisScope;
import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInspection.reference.RefElement;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.PsiRecursiveElementVisitor;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

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
    scope.accept(new PsiRecursiveElementVisitor() {
      @Override
      public void visitErrorElement(PsiErrorElement element) {
        super.visitErrorElement(element);
        CommonProblemDescriptor descriptor;
        final TextRange textRange = element.getTextRange();
        if (textRange.getLength() > 0) {
          descriptor = manager.createProblemDescriptor(element, element.getErrorDescription(), ProblemHighlightType.ERROR, null);
        }
        else {
          descriptor = manager.createProblemDescriptor(element.getParent(),
                                                       new TextRange(element.getStartOffsetInParent(), element.getStartOffsetInParent()+1),
                                                       element.getErrorDescription(), ProblemHighlightType.ERROR);
        }

        final RefElement refElement = globalContext.getRefManager().getReference(element.getContainingFile());
        problemDescriptionsProcessor.addProblemElement(refElement, descriptor);
      }
    });
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
}
