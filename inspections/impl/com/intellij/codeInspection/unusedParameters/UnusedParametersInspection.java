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
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ex.*;
import com.intellij.codeInspection.reference.*;
import com.intellij.codeInspection.util.XMLExportUtl;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.IconLoader;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiReferenceProcessor;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.refactoring.changeSignature.ChangeSignatureProcessor;
import com.intellij.refactoring.changeSignature.ParameterInfo;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;

public class UnusedParametersInspection extends FilteringInspectionTool {
  private UnusedParametersFilter myFilter;
  private UnusedParametersComposer myComposer;

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
                    if (!refMethod.isStatic() && !refMethod.isConstructor() && refMethod.getAccessModifier() != PsiModifier.PRIVATE) {
                      PsiMethod[] derived = helper.findOverridingMethods(psiMethod, GlobalSearchScope.projectScope(getContext().getProject()), true);
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

  protected void resetFilter() {
    myFilter = null;
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
            final String attributeKey = getTextAttributeKey(unusedParameter, severity, ProblemHighlightType.LIKE_UNUSED_SYMBOL);
            problemClassElement.setAttribute("severity", severity.myName);
            problemClassElement.setAttribute("attribute_key", attributeKey);

            element.addContent(problemClassElement);

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

  private class AcceptSuggested extends QuickFixAction {
    private AcceptSuggested() {
      super(InspectionsBundle.message("inspection.unused.parameter.delete.quickfix"),IconLoader.getIcon("/actions/cancel.png"), null, UnusedParametersInspection.this);
    }

    protected boolean applyFix(RefElement[] refElements) {
      for (RefElement refElement : refElements) {
        if (refElement instanceof RefMethod) {
          RefMethod refMethod = (RefMethod)refElement;
          PsiMethod psiMethod = (PsiMethod)refMethod.getElement();

          if (psiMethod == null) continue;

          ArrayList<PsiElement> psiParameters = new ArrayList<PsiElement>();
          for (final RefParameter refParameter : UnusedParametersFilter.getUnusedParameters(refMethod)) {
            psiParameters.add(refParameter.getElement());
          }

          removeUnusedParameterViaChangeSignature(psiMethod, psiParameters);

          getFilter().ignore(refMethod);
        }
      }

      return true;
    }
  }

  public String getDisplayName() {
    return InspectionsBundle.message("inspection.unused.parameter.display.name");
  }

  public String getGroupDisplayName() {
    return GroupNames.DECLARATION_REDUNDANCY;
  }

  public String getShortName() {
    return "UnusedParameters";
  }

  public HTMLComposer getComposer() {
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
