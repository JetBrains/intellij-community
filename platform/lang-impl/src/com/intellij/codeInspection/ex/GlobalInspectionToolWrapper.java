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

  public GlobalInspectionToolWrapper(@NotNull InspectionEP ep) {
    super(ep);
  }

  private GlobalInspectionToolWrapper(@NotNull GlobalInspectionToolWrapper other) {
    super(other);
  }

  @NotNull
  @Override
  public InspectionToolWrapper<GlobalInspectionTool, InspectionEP> createCopy() {
    return new GlobalInspectionToolWrapper(this);
  }

  @Override
  public void initialize(@NotNull GlobalInspectionContextImpl context) {
    super.initialize(context);
    final RefGraphAnnotator annotator = getTool().getAnnotator(getRefManager());
    if (annotator != null) {
      ((RefManagerImpl)getRefManager()).registerGraphAnnotator(annotator);
    }
  }

  @Override
  public void runInspection(@NotNull final AnalysisScope scope, @NotNull final InspectionManager manager) {
    throw new RuntimeException();
    //getTool().runInspection(scope, manager, getContext(), this);
  }

  @Override
  public boolean queryExternalUsagesRequests(@NotNull final InspectionManager manager) {
    throw new RuntimeException();
    //return getTool().queryExternalUsagesRequests(manager, getContext(), this);
  }

  @Override
  @NotNull
  public JobDescriptor[] getJobDescriptors(@NotNull GlobalInspectionContext context) {
    final JobDescriptor[] additionalJobs = getTool().getAdditionalJobs();
    if (additionalJobs == null) {
      return getTool().isGraphNeeded() ? context.getStdJobDescriptors().BUILD_GRAPH_ONLY : JobDescriptor.EMPTY_ARRAY;
    }
    else {
      return getTool().isGraphNeeded() ? ArrayUtil.append(additionalJobs, context.getStdJobDescriptors().BUILD_GRAPH) : additionalJobs;
    }
  }

  @Override
  public boolean isGraphNeeded() {
    throw new RuntimeException();
    //return getTool().isGraphNeeded();
  }

  public void processFile(@NotNull final AnalysisScope analysisScope,
                          @NotNull final InspectionManager manager,
                          @NotNull final GlobalInspectionContext context,
                          final boolean filterSuppressed) {
    context.getRefManager().iterate(new RefVisitor() {
      @Override public void visitElement(@NotNull RefEntity refEntity) {
        CommonProblemDescriptor[] descriptors = getTool().checkElement(refEntity, analysisScope, manager, context, GlobalInspectionToolWrapper.this);
        if (descriptors != null) {
          addProblemElement(refEntity, filterSuppressed, descriptors);
        }
      }
    });
  }

  @NotNull
  @Override
  public HTMLComposerImpl getComposer() {
    return new DescriptorComposer(this) {
      @Override
      protected void composeAdditionalDescription(@NotNull final StringBuffer buf, @NotNull final RefEntity refEntity) {
        getTool().compose(buf, refEntity, this);
      }
    };
  }

  @Override
  @Nullable
  public IntentionAction findQuickFixes(final CommonProblemDescriptor problemDescriptor, final String hint) {
    final QuickFix fix = getTool().getQuickFix(hint);
    if (fix == null) {
      return null;
    }
    if (problemDescriptor instanceof ProblemDescriptor) {
      final ProblemDescriptor descriptor = new ProblemDescriptorImpl(((ProblemDescriptor)problemDescriptor).getStartElement(),
                                                                     ((ProblemDescriptor)problemDescriptor).getEndElement(),
                                                                     problemDescriptor.getDescriptionTemplate(),
                                                                     new LocalQuickFix[]{(LocalQuickFix)fix},
                                                                     ProblemHighlightType.GENERIC_ERROR_OR_WARNING, false, null, false);
      return QuickFixWrapper.wrap(descriptor, 0);
    }
    return new IntentionAction() {
      @Override
      @NotNull
      public String getText() {
        return fix.getName();
      }

      @Override
      @NotNull
      public String getFamilyName() {
        return fix.getFamilyName();
      }

      @Override
      public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
        return true;
      }

      @Override
      public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
        fix.applyFix(project, problemDescriptor); //todo check type consistency
      }

      @Override
      public boolean startInWriteAction() {
        return true;
      }
    };
  }

  public boolean worksInBatchModeOnly() {
    return getTool().worksInBatchModeOnly();
  }
}
