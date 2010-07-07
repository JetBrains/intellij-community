/*
 * Copyright (c) 2006 Your Corporation. All Rights Reserved.
 */
package com.intellij.codeInspection.ex;

import com.intellij.analysis.AnalysisScope;
import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.reference.RefEntity;
import com.intellij.codeInspection.reference.RefGraphAnnotator;
import com.intellij.codeInspection.reference.RefManagerImpl;
import com.intellij.codeInspection.reference.RefVisitor;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.PsiFile;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * User: anna
 * Date: 28-Dec-2005
 */
public class GlobalInspectionToolWrapper extends DescriptorProviderInspection {
  @NotNull private final GlobalInspectionTool myTool;

  public GlobalInspectionToolWrapper(@NotNull GlobalInspectionTool globalInspectionTool) {
    myTool = globalInspectionTool;
  }

  public void initialize(@NotNull GlobalInspectionContextImpl context) {
    super.initialize(context);
    final RefGraphAnnotator annotator = myTool.getAnnotator(getRefManager());
    if (annotator != null) {
      ((RefManagerImpl)getRefManager()).registerGraphAnnotator(annotator);
    }
  }

  public void runInspection(final AnalysisScope scope, final InspectionManager manager) {
    myTool.runInspection(scope, manager, getContext(), this);
  }

  public boolean queryExternalUsagesRequests(final InspectionManager manager) {
    return myTool.queryExternalUsagesRequests(manager, getContext(), this);
  }

  @NotNull
  public JobDescriptor[] getJobDescriptors() {
    final JobDescriptor[] additionalJobs = myTool.getAdditionalJobs();
    if (additionalJobs == null) {
      return isGraphNeeded() ? GlobalInspectionContextImpl.BUILD_GRAPH_ONLY : JobDescriptor.EMPTY_ARRAY;
    }
    else {
      return isGraphNeeded() ? ArrayUtil.append(additionalJobs, GlobalInspectionContextImpl.BUILD_GRAPH) : additionalJobs;
    }
  }

  @NotNull
  public String getDisplayName() {
    return myTool.getDisplayName();
  }

  @NotNull
  public String getGroupDisplayName() {
    return myTool.getGroupDisplayName();
  }

  @NotNull
  @Override
  public String[] getGroupPath() {
    return myTool.getGroupPath();
  }

  @NotNull
  @NonNls
  public String getShortName() {
    return myTool.getShortName();
  }

  public boolean isEnabledByDefault() {
    return myTool.isEnabledByDefault();
  }

  @NotNull
  public HighlightDisplayLevel getDefaultLevel() {
    return myTool.getDefaultLevel();
  }

  public void readSettings(Element element) throws InvalidDataException {
    myTool.readSettings(element);
  }

  public void writeSettings(Element element) throws WriteExternalException {
    myTool.writeSettings(element);
  }

  public JComponent createOptionsPanel() {
    return myTool.createOptionsPanel();
  }

  public boolean isGraphNeeded() {
    return myTool.isGraphNeeded();
  }

  @NotNull public GlobalInspectionTool getTool() {
    return myTool;
  }

  public void processFile(final AnalysisScope analysisScope,
                          final InspectionManager manager,
                          final GlobalInspectionContext context,
                          final boolean filterSuppressed) {
    context.getRefManager().iterate(new RefVisitor() {
      @Override public void visitElement(RefEntity refEntity) {
        CommonProblemDescriptor[] descriptors = myTool.checkElement(refEntity, analysisScope, manager, context, GlobalInspectionToolWrapper.this);
        if (descriptors != null) {
          addProblemElement(refEntity, filterSuppressed, descriptors);
        }
      }
    });
  }
  public void projectOpened(Project project) {
    myTool.projectOpened(project);
  }

  public void projectClosed(Project project) {
    myTool.projectClosed(project);
  }


  public HTMLComposerImpl getComposer() {
    return new DescriptorComposer(this) {
      protected void composeAdditionalDescription(final StringBuffer buf, final RefEntity refEntity) {
        myTool.compose(buf, refEntity, this);
      }
    };
  }

  @Nullable
  public IntentionAction findQuickFixes(final CommonProblemDescriptor problemDescriptor, final String hint) {
    final QuickFix fix = myTool.getQuickFix(hint);
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

  protected Class<? extends InspectionProfileEntry> getDescriptionContextClass() {
    return myTool.getClass();
  }

  @Nullable
  public String getStaticDescription() {
    return myTool.getStaticDescription();
  }

  @Nullable
  public SuppressIntentionAction[] getSuppressActions() {
    if (myTool instanceof CustomSuppressableInspectionTool) {
      return ((CustomSuppressableInspectionTool)myTool).getSuppressActions(null);
    }
    return super.getSuppressActions();
  }
}
