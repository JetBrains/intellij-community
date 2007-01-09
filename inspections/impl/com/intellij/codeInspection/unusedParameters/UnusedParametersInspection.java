/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Dec 24, 2001
 * Time: 2:46:32 PM
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.codeInspection.unusedParameters;

import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.ex.*;
import com.intellij.codeInspection.reference.*;
import com.intellij.codeInspection.util.XMLExportUtl;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.psi.*;
import com.intellij.psi.search.PsiReferenceProcessor;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.changeSignature.ChangeSignatureProcessor;
import com.intellij.refactoring.changeSignature.ParameterInfo;
import com.intellij.util.IncorrectOperationException;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;

public class UnusedParametersInspection extends FilteringInspectionTool {
  private UnusedParametersFilter myFilter;
  private UnusedParametersComposer myComposer;
  @NonNls private static final String QUICK_FIX_NAME = InspectionsBundle.message("inspection.unused.parameter.delete.quickfix");

  public UnusedParametersInspection() {

    myQuickFixActions = new QuickFixAction[] {new AcceptSuggested()};
  }

  private QuickFixAction[] myQuickFixActions;

  public void runInspection(AnalysisScope scope, final InspectionManager manager) {
    // Do additional search of problem elements outside the scope.
    final Runnable action = new Runnable() {
      public void run() {
        if (getRefManager().getScope().getScopeType() != AnalysisScope.PROJECT) {
          ProgressManager.getInstance().runProcess(new Runnable() {
            public void run() {
              final UnusedParametersFilter filter = getFilter();
              final PsiSearchHelper helper = PsiManager.getInstance(getContext().getProject()).getSearchHelper();

              getRefManager().iterate(new RefVisitor() {
                public void visitElement(RefEntity refEntity) {
                  if (refEntity instanceof RefElement && filter.accepts((RefElement)refEntity)) {
                    RefMethod refMethod = (RefMethod) refEntity;
                    PsiMethod psiMethod = (PsiMethod) refMethod.getElement();
                    if (!refMethod.isStatic() && !refMethod.isConstructor() && !PsiModifier.PRIVATE.equals(refMethod.getAccessModifier())) {
                      PsiMethod[] derived = helper.findOverridingMethods(psiMethod, psiMethod.getUseScope(), true);
                      final ArrayList<RefParameter> unusedParameters = UnusedParametersFilter.getUnusedParameters(refMethod);
                      for (final RefParameter refParameter : unusedParameters) {
                        int idx = refParameter.getIndex();

                        if (refMethod.isAbstract() && derived.length == 0) {
                          refParameter.parameterReferenced(false);
                        }
                        else {
                          final boolean[] found = new boolean[]{false};
                          for (int i = 0; i < derived.length && !found[0]; i++) {
                            if (!getRefManager().getScope().contains(derived[i])) {
                              PsiParameter psiParameter = derived[i].getParameterList().getParameters()[idx];
                              helper.processReferences(new PsiReferenceProcessor() {
                                public boolean execute(PsiReference element) {
                                  refParameter.parameterReferenced(false);
                                  found[0] = true;
                                  return false;
                                }
                              }, psiParameter, helper.getUseScope(psiParameter), false);
                            }
                          }
                        }
                      }
                    }
                  }
                }
              });
            }
          }, null);
        }
      }
    };
    ApplicationManager.getApplication().runReadAction(action);
  }

  public UnusedParametersFilter getFilter() {
    if (myFilter == null) {
      myFilter = new UnusedParametersFilter(this);
    }
    return myFilter;
  }

  public void exportResults(final Element parentNode) {
    final UnusedParametersFilter filter = getFilter();
    getRefManager().iterate(new RefVisitor() {
      public void visitElement(RefEntity refEntity) {
        if (refEntity instanceof RefElement && filter.accepts((RefElement)refEntity)) {
          ArrayList<RefParameter> unusedParameters = UnusedParametersFilter.getUnusedParameters((RefMethod)refEntity);
          for (RefParameter unusedParameter : unusedParameters) {
            Element element = XMLExportUtl.createElement(refEntity, parentNode, -1, null);
            @NonNls Element problemClassElement = new Element(InspectionsBundle.message("inspection.export.results.problem.element.tag"));
            problemClassElement.addContent(InspectionsBundle.message("inspection.unused.parameter.export.results"));

            final HighlightSeverity severity = getCurrentSeverity(unusedParameter);
            final String attributeKey = getTextAttributeKey(severity, ProblemHighlightType.LIKE_UNUSED_SYMBOL);
            problemClassElement.setAttribute("severity", severity.myName);
            problemClassElement.setAttribute("attribute_key", attributeKey);

            element.addContent(problemClassElement);

            @NonNls Element hintsElement = new Element("hints");
            @NonNls Element hintElement = new Element("hint");
            hintElement.setAttribute("value", String.valueOf(unusedParameter.getIndex()));
            hintsElement.addContent(hintElement);

            Element descriptionElement = new Element(InspectionsBundle.message("inspection.export.results.description.tag"));
            descriptionElement
              .addContent(InspectionsBundle.message("inspection.unused.parameter.export.results.description", unusedParameter.getName()));
            element.addContent(descriptionElement);
          }
        }
      }
    });
  }

  public QuickFixAction[] getQuickFixes(final RefEntity[] refElements) {
    return myQuickFixActions;
  }

  @NotNull
  public JobDescriptor[] getJobDescriptors() {
    return new JobDescriptor[] {GlobalInspectionContextImpl.BUILD_GRAPH, GlobalInspectionContextImpl.FIND_EXTERNAL_USAGES};
  }


  @Nullable
  public IntentionAction findQuickFixes(final CommonProblemDescriptor descriptor, final String hint) {
    return new IntentionAction() {
      @NotNull
      public String getText() {
        return QUICK_FIX_NAME;
      }

      @NotNull
      public String getFamilyName() {
        return getText();
      }

      public boolean isAvailable(Project project, Editor editor, PsiFile file) {
        return true;
      }

      public void invoke(Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
        if (hint == null) return;
        if (descriptor instanceof ProblemDescriptor) {
          int idx;
          try {
            idx = Integer.parseInt(hint);
          }
          catch (NumberFormatException e) {
            return;
          }
          final PsiMethod psiMethod = PsiTreeUtil.getParentOfType(((ProblemDescriptor)descriptor).getPsiElement(), PsiMethod.class);
          if (psiMethod != null) {

            final PsiParameter parameter = psiMethod.getParameterList().getParameters()[idx];

            if (descriptor.getDescriptionTemplate().indexOf(parameter.getName()) == - 1) return;

            final ArrayList<PsiElement> parametersToDelete = new ArrayList<PsiElement>();
            parametersToDelete.add(parameter);
            removeUnusedParameterViaChangeSignature(psiMethod, parametersToDelete);
          }
        }
      }

      public boolean startInWriteAction() {
        return true;
      }
    };
  }

  private class AcceptSuggested extends QuickFixAction {
    private AcceptSuggested() {
      super(QUICK_FIX_NAME, IconLoader.getIcon("/actions/cancel.png"), null, UnusedParametersInspection.this);
    }

    protected boolean applyFix(RefElement[] refElements) {
      boolean needToRefresh = false;
      for (RefElement refElement : refElements) {
        if (refElement instanceof RefMethod) {
          RefMethod refMethod = (RefMethod)refElement;
          PsiMethod psiMethod = (PsiMethod)refMethod.getElement();

          if (psiMethod == null) continue;

          ArrayList<PsiElement> psiParameters = new ArrayList<PsiElement>();
          for (final RefParameter refParameter : UnusedParametersFilter.getUnusedParameters(refMethod)) {
            psiParameters.add(refParameter.getElement());
          }

          final PsiModificationTracker tracker = psiMethod.getManager().getModificationTracker();
          final long startModificationCount = tracker.getModificationCount();

          removeUnusedParameterViaChangeSignature(psiMethod, psiParameters);
          if (startModificationCount != tracker.getModificationCount()) {
            getFilter().addIgnoreList(refMethod);
            needToRefresh = true;
          }
        }
      }

      return needToRefresh;
    }
  }

  @NotNull
  public String getDisplayName() {
    return InspectionsBundle.message("inspection.unused.parameter.display.name");
  }

  @NotNull
  public String getGroupDisplayName() {
    return GroupNames.DECLARATION_REDUNDANCY;
  }

  @NotNull
  public String getShortName() {
    return "UnusedParameters";
  }

  public HTMLComposerImpl getComposer() {
    if (myComposer == null) {
      myComposer = new UnusedParametersComposer(getFilter(), this);
    }
    return myComposer;
  }

  private void removeUnusedParameterViaChangeSignature(final PsiMethod psiMethod, final Collection<PsiElement> parametersToDelete) {
    ArrayList<ParameterInfo> newParameters = new ArrayList<ParameterInfo>();
    PsiParameter[] oldParameters = psiMethod.getParameterList().getParameters();
    for (int i = 0; i < oldParameters.length; i++) {
      PsiParameter oldParameter = oldParameters[i];
      if (!parametersToDelete.contains(oldParameter)) {
        newParameters.add(new ParameterInfo(i, oldParameter.getName(), oldParameter.getType()));
      }
    }

    ParameterInfo[] parameterInfos = newParameters.toArray(new ParameterInfo[newParameters.size()]);

    ChangeSignatureProcessor csp = new ChangeSignatureProcessor(getContext().getProject(),
                                                                psiMethod,
                                                                false,
                                                                null,
                                                                psiMethod.getName(),
                                                                psiMethod.getReturnType(),
                                                                parameterInfos);

    csp.run();
  }
}
