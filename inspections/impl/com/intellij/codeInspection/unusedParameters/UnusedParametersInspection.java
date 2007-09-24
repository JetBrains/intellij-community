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
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.reference.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.*;
import com.intellij.psi.search.PsiReferenceProcessor;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.changeSignature.ChangeSignatureProcessor;
import com.intellij.refactoring.changeSignature.ParameterInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class UnusedParametersInspection extends GlobalInspectionTool {

  @Nullable
  public CommonProblemDescriptor[] checkElement(final RefEntity refEntity,
                                                final AnalysisScope scope,
                                                final InspectionManager manager,
                                                final GlobalInspectionContext globalContext,
                                                final ProblemDescriptionsProcessor processor) {
    if (refEntity instanceof RefMethod) {
      final RefMethod refMethod = (RefMethod)refEntity;

      if (refMethod.isSyntheticJSP()) return null;

      if (refMethod.isExternalOverride()) return null;

      if (!(refMethod.isStatic() || refMethod.isConstructor()) && refMethod.getSuperMethods().size() > 0) return null;

      if ((refMethod.isAbstract() || refMethod.getOwnerClass().isInterface()) && refMethod.getDerivedMethods().isEmpty()) return null;

      if (RefUtil.isEntryPoint(refMethod)) return null;

      final ArrayList<RefParameter> unusedParameters = getUnusedParameters(refMethod);

      if (unusedParameters.size() == 0) return null;

      final List<ProblemDescriptor> result = new ArrayList<ProblemDescriptor>();
      for (RefParameter refParameter : unusedParameters) {
        final PsiIdentifier psiIdentifier = refParameter.getElement().getNameIdentifier();
        if (psiIdentifier != null) {
          result.add(manager.createProblemDescriptor(psiIdentifier,
                                                     refMethod.isAbstract()
                                                     ? InspectionsBundle.message("inspection.unused.parameter.composer")
                                                     : InspectionsBundle.message("inspection.unused.parameter.composer1"),
                                                     new AcceptSuggested(globalContext.getRefManager(), processor, refParameter.toString()),
                                                     ProblemHighlightType.LIKE_UNUSED_SYMBOL));
        }
      }
      return result.toArray(new CommonProblemDescriptor[result.size()]);
    }
    return null;
  }

  public boolean queryExternalUsagesRequests(final InspectionManager manager,
                                             final GlobalInspectionContext globalContext,
                                             final ProblemDescriptionsProcessor processor) {
    for (SmartRefElementPointer entryPoint : globalContext.getRefManager().getEntryPointsManager().getEntryPoints()) {
      final RefEntity refElement = entryPoint.getRefElement();
      if (refElement != null) {
        processor.ignoreElement(refElement);
      }
    }
    final PsiSearchHelper helper = PsiManager.getInstance(globalContext.getProject()).getSearchHelper();
    final AnalysisScope scope = globalContext.getRefManager().getScope();
    globalContext.getRefManager().iterate(new RefVisitor() {
      public void visitElement(RefEntity refEntity) {
        if (refEntity instanceof RefMethod) {
          RefMethod refMethod = (RefMethod)refEntity;
          final PsiModifierListOwner element = refMethod.getElement();
          if (element instanceof PsiMethod) { //implicit construcors are invisible
            PsiMethod psiMethod = (PsiMethod)element;
            if (!refMethod.isStatic() && !refMethod.isConstructor() && !PsiModifier.PRIVATE.equals(refMethod.getAccessModifier())) {
              final ArrayList<RefParameter> unusedParameters = getUnusedParameters(refMethod);
              if (unusedParameters.isEmpty()) return;
              PsiMethod[] derived = helper.findOverridingMethods(psiMethod, psiMethod.getUseScope(), true);
              for (final RefParameter refParameter : unusedParameters) {
                if (refMethod.isAbstract() && derived.length == 0) {
                  refParameter.parameterReferenced(false);
                  processor.ignoreElement(refParameter);
                }
                else {
                  int idx = refParameter.getIndex();
                  final boolean[] found = new boolean[]{false};
                  for (int i = 0; i < derived.length && !found[0]; i++) {
                    if (!scope.contains(derived[i])) {
                      PsiParameter psiParameter = derived[i].getParameterList().getParameters()[idx];
                      helper.processReferences(new PsiReferenceProcessor() {
                        public boolean execute(PsiReference element) {
                          refParameter.parameterReferenced(false);
                          processor.ignoreElement(refParameter);
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
      }
    });
    return false;
  }

  @Nullable
  public String getHint(final QuickFix fix) {
    return ((AcceptSuggested)fix).getHint();
  }

  @Nullable
  public QuickFix getQuickFix(final String hint) {
    return new AcceptSuggested(null, null, hint);
  }

  public void compose(final StringBuffer buf, final RefEntity refEntity, final HTMLComposer composer) {
    if (refEntity instanceof RefMethod) {
      final RefMethod refMethod = (RefMethod)refEntity;
      composer.appendDerivedMethods(buf, refMethod);
      composer.appendSuperMethods(buf, refMethod);
    }
  }

  public static ArrayList<RefParameter> getUnusedParameters(RefMethod refMethod) {
    boolean checkDeep = !refMethod.isStatic() && !refMethod.isConstructor();
    ArrayList<RefParameter> res = new ArrayList<RefParameter>();
    RefParameter[] methodParameters = refMethod.getParameters();
    RefParameter[] result = new RefParameter[methodParameters.length];
    System.arraycopy(methodParameters, 0, result, 0, methodParameters.length);

    clearUsedParameters(refMethod, result, checkDeep);

    for (RefParameter parameter : result) {
      if (parameter != null) {
        res.add(parameter);
      }
    }

    return res;
  }

  private static void clearUsedParameters(@NotNull RefMethod refMethod, RefParameter[] params, boolean checkDeep) {
    RefParameter[] methodParms = refMethod.getParameters();

    for (int i = 0; i < methodParms.length; i++) {
      if (methodParms[i].isUsedForReading()) params[i] = null;
    }

    if (checkDeep) {
      for (RefMethod refOverride : refMethod.getDerivedMethods()) {
        clearUsedParameters(refOverride, params, checkDeep);
      }
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


  private static class AcceptSuggested implements LocalQuickFix {
    private RefManager myManager;
    private String myHint;
    private ProblemDescriptionsProcessor myProcessor;

    public AcceptSuggested(final RefManager manager, final ProblemDescriptionsProcessor processor, final String hint) {
      myManager = manager;
      myProcessor = processor;
      myHint = hint;
    }

    public String getHint() {
      return myHint;
    }

    @NotNull
    public String getName() {
      return InspectionsBundle.message("inspection.unused.parameter.delete.quickfix");
    }

    @NotNull
    public String getFamilyName() {
      return getName();
    }

    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      final PsiMethod psiMethod = PsiTreeUtil.getParentOfType(descriptor.getPsiElement(), PsiMethod.class);
      if (psiMethod != null) {
        ArrayList<PsiElement> psiParameters = new ArrayList<PsiElement>();
        if (myManager != null) {
          for (final RefParameter refParameter : getUnusedParameters((RefMethod)myManager.getReference(psiMethod))) {
            psiParameters.add(refParameter.getElement());
          }
        }
        else {
          final PsiParameter[] parameters = psiMethod.getParameterList().getParameters();
          for (PsiParameter parameter : parameters) {
            if (Comparing.strEqual(parameter.getName(), myHint)) {
              psiParameters.add(parameter);
              break;
            }
          }
        }

        final PsiModificationTracker tracker = psiMethod.getManager().getModificationTracker();
        final long startModificationCount = tracker.getModificationCount();

        removeUnusedParameterViaChangeSignature(psiMethod, psiParameters);

        if (myManager != null && startModificationCount != tracker.getModificationCount()) {
          myProcessor.ignoreElement(myManager.getReference(psiMethod));
        }
      }
    }

    private static void removeUnusedParameterViaChangeSignature(final PsiMethod psiMethod,
                                                                final Collection<PsiElement> parametersToDelete) {
      ArrayList<ParameterInfo> newParameters = new ArrayList<ParameterInfo>();
      PsiParameter[] oldParameters = psiMethod.getParameterList().getParameters();
      for (int i = 0; i < oldParameters.length; i++) {
        PsiParameter oldParameter = oldParameters[i];
        if (!parametersToDelete.contains(oldParameter)) {
          newParameters.add(new ParameterInfo(i, oldParameter.getName(), oldParameter.getType()));
        }
      }

      ParameterInfo[] parameterInfos = newParameters.toArray(new ParameterInfo[newParameters.size()]);

      ChangeSignatureProcessor csp = new ChangeSignatureProcessor(psiMethod.getProject(), psiMethod, false, null, psiMethod.getName(),
                                                                  psiMethod.getReturnType(), parameterInfos);

      csp.run();
    }

  }
}
