package com.intellij.codeInspection.ex;

import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.reference.RefEntity;
import com.intellij.codeInspection.reference.RefGraphAnnotator;
import com.intellij.codeInspection.reference.RefManagerImpl;
import com.intellij.codeInspection.reference.RefVisitor;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * User: anna
 * Date: 28-Dec-2005
 */
public class GlobalInspectionToolWrapper extends InspectionToolWrapper<GlobalInspectionTool, InspectionEP> {

  public GlobalInspectionToolWrapper(@NotNull GlobalInspectionTool globalInspectionTool) {
    super(globalInspectionTool);
  }

  public GlobalInspectionToolWrapper(InspectionEP ep) {
    super(ep);
  }

  public GlobalInspectionToolWrapper(InspectionEP ep, GlobalInspectionTool tool) {
    super(ep, tool);
  }

  @Override
  public InspectionToolWrapper<GlobalInspectionTool, InspectionEP> createCopy(InspectionToolWrapper<GlobalInspectionTool, InspectionEP> from) {
    return new GlobalInspectionToolWrapper(from.myEP, from.myTool);
  }

  public void initialize(@NotNull GlobalInspectionContextImpl context) {
    super.initialize(context);
    final RefGraphAnnotator annotator = getTool().getAnnotator(getRefManager());
    if (annotator != null) {
      ((RefManagerImpl)getRefManager()).registerGraphAnnotator(annotator);
    }
  }

  public void runInspection(@NotNull final AnalysisScope scope, @NotNull final InspectionManager manager) {
    getTool().runInspection(scope, manager, getContext(), this);
  }

  public boolean queryExternalUsagesRequests(final InspectionManager manager) {
    return getTool().queryExternalUsagesRequests(manager, getContext(), this);
  }

  @NotNull
  public JobDescriptor[] getJobDescriptors(GlobalInspectionContext context) {
    final JobDescriptor[] additionalJobs = getTool().getAdditionalJobs();
    if (additionalJobs == null) {
      return isGraphNeeded() ? ((GlobalInspectionContextImpl)context).BUILD_GRAPH_ONLY : JobDescriptor.EMPTY_ARRAY;
    }
    else {
      return isGraphNeeded() ? ArrayUtil.append(additionalJobs, ((GlobalInspectionContextImpl)context).BUILD_GRAPH) : additionalJobs;
    }
  }

  public boolean isGraphNeeded() {
    return getTool().isGraphNeeded();
  }

  public void processFile(final AnalysisScope analysisScope,
                          final InspectionManager manager,
                          final GlobalInspectionContext context,
                          final boolean filterSuppressed) {
    context.getRefManager().iterate(new RefVisitor() {
      @Override public void visitElement(RefEntity refEntity) {
        CommonProblemDescriptor[] descriptors = getTool()
          .checkElement(refEntity, analysisScope, manager, context, GlobalInspectionToolWrapper.this);
        if (descriptors != null) {
          addProblemElement(refEntity, filterSuppressed, descriptors);
        }
      }
    });
  }

  public HTMLComposerImpl getComposer() {
    return new DescriptorComposer(this) {
      protected void composeAdditionalDescription(final StringBuffer buf, final RefEntity refEntity) {
        getTool().compose(buf, refEntity, this);
      }
    };
  }

  @Nullable
  public IntentionAction findQuickFixes(final CommonProblemDescriptor problemDescriptor, final String hint) {
    final QuickFix fix = getTool().getQuickFix(hint);
    if (fix != null) {
      if (problemDescriptor instanceof ProblemDescriptor) {
        final ProblemDescriptor descriptor = new ProblemDescriptorImpl(((ProblemDescriptor)problemDescriptor).getStartElement(),
                                                                       ((ProblemDescriptor)problemDescriptor).getEndElement(),
                                                                       problemDescriptor.getDescriptionTemplate(),
                                                                       new LocalQuickFix[]{(LocalQuickFix)fix},
                                                                       ProblemHighlightType.GENERIC_ERROR_OR_WARNING, false, null, false);
        return QuickFixWrapper.wrap(descriptor, 0);
      }
      else {
        return new IntentionAction() {
          @NotNull
          public String getText() {
            return fix.getName();
          }

          @NotNull
          public String getFamilyName() {
            return fix.getFamilyName();
          }

          public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
            return true;
          }

          public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
            fix.applyFix(project, problemDescriptor); //todo check type consistency
          }

          public boolean startInWriteAction() {
            return true;
          }
        };
      }
    }
    return null;
  }

  public boolean worksInBatchModeOnly() {
    return getTool().worksInBatchModeOnly();
  }
}
