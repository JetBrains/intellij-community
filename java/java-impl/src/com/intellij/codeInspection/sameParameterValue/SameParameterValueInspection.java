// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.sameParameterValue;

import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInsight.daemon.impl.UnusedSymbolUtil;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.deadCode.UnusedDeclarationInspectionBase;
import com.intellij.codeInspection.reference.*;
import com.intellij.codeInspection.unusedSymbol.VisibilityModifierChooser;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.LabeledComponent;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.search.searches.OverridingMethodsSearch;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiFormatUtilBase;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.changeSignature.ChangeSignatureProcessor;
import com.intellij.refactoring.changeSignature.ParameterInfoImpl;
import com.intellij.refactoring.safeDelete.JavaSafeDeleteProcessor;
import com.intellij.refactoring.ui.ConflictsDialog;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.refactoring.util.InlineUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ObjectUtils;
import com.intellij.util.VisibilityUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.*;

import static com.intellij.codeInspection.reference.RefParameter.VALUE_IS_NOT_CONST;
import static com.intellij.codeInspection.reference.RefParameter.VALUE_UNDEFINED;

/**
 * @author max
 */
public class SameParameterValueInspection extends GlobalJavaBatchInspectionTool {
  private static final Logger LOG = Logger.getInstance(SameParameterValueInspection.class);
  @PsiModifier.ModifierConstant
  private static final String DEFAULT_HIGHEST_MODIFIER = PsiModifier.PROTECTED;
  @PsiModifier.ModifierConstant
  public String highestModifier = DEFAULT_HIGHEST_MODIFIER;

  @Nullable
  @Override
  public JComponent createOptionsPanel() {
    LabeledComponent<VisibilityModifierChooser> component = LabeledComponent.create(new VisibilityModifierChooser(() -> true,
                                                                                                                  highestModifier,
                                                                                                                  (newModifier) -> highestModifier = newModifier),
                                                                                    "Methods to report:",
                                                                                    BorderLayout.WEST);

    JPanel panel = new JPanel(new GridBagLayout());
    panel.add(component, new GridBagConstraints(0, 0, 1, 1, 1.0, 1.0, GridBagConstraints.FIRST_LINE_START, GridBagConstraints.NORTHEAST, JBUI.emptyInsets(), 0, 0));
    return panel;
  }


  protected LocalQuickFix createFix(String paramName, String value) {
    return new InlineParameterValueFix(paramName, value);
  }

  @Override
  @Nullable
  public CommonProblemDescriptor[] checkElement(@NotNull RefEntity refEntity,
                                                @NotNull AnalysisScope scope,
                                                @NotNull InspectionManager manager,
                                                @NotNull GlobalInspectionContext globalContext,
                                                @NotNull ProblemDescriptionsProcessor processor) {
    List<ProblemDescriptor> problems = null;
    if (refEntity instanceof RefMethod) {
      final RefMethod refMethod = (RefMethod)refEntity;

      if (refMethod.hasSuperMethods() ||
          VisibilityUtil.compare(refMethod.getAccessModifier(), highestModifier) < 0 ||
          refMethod.isEntry()) return null;

      RefParameter[] parameters = refMethod.getParameters();
      for (RefParameter refParameter : parameters) {
        Object value = refParameter.getActualConstValue();
        if (value != VALUE_IS_NOT_CONST && value != VALUE_UNDEFINED) {
          if (!globalContext.shouldCheck(refParameter, this)) continue;
          if (problems == null) problems = new ArrayList<>(1);
          problems.add(registerProblem(manager, refParameter.getElement(), value, refParameter.isUsedForWriting()));
        }
      }
    }

    return problems == null ? null : problems.toArray(CommonProblemDescriptor.EMPTY_ARRAY);
  }

  @Override
  protected boolean queryExternalUsagesRequests(@NotNull final RefManager manager, @NotNull final GlobalJavaInspectionContext globalContext,
                                                @NotNull final ProblemDescriptionsProcessor processor) {
    manager.iterate(new RefJavaVisitor() {
      @Override public void visitElement(@NotNull RefEntity refEntity) {
        if (refEntity instanceof RefElement && processor.getDescriptions(refEntity) != null) {
          refEntity.accept(new RefJavaVisitor() {
            @Override public void visitMethod(@NotNull final RefMethod refMethod) {
              globalContext.enqueueMethodUsagesProcessor(refMethod, new GlobalJavaInspectionContext.UsagesProcessor() {
                @Override
                public boolean process(PsiReference psiReference) {
                  processor.ignoreElement(refMethod);
                  return false;
                }
              });
            }
          });
        }
      }
    });

    return false;
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionsBundle.message("inspection.same.parameter.display.name");
  }

  @Override
  @NotNull
  public String getGroupDisplayName() {
    return GroupNames.DECLARATION_REDUNDANCY;
  }

  @Override
  @NotNull
  public String getShortName() {
    return "SameParameterValue";
  }

  @Override
  @Nullable
  public QuickFix getQuickFix(final String hint) {
    if (hint == null) return null;
    final int spaceIdx = hint.indexOf(' ');
    if (spaceIdx == -1 || spaceIdx >= hint.length() - 1) return null; //invalid hint
    final String paramName = hint.substring(0, spaceIdx);
    final String value = hint.substring(spaceIdx + 1);
    return createFix(paramName, value);
  }

  @Override
  @Nullable
  public String getHint(@NotNull final QuickFix fix) {
    return fix.toString();
  }

  @Nullable
  @Override
  public LocalInspectionTool getSharedLocalInspectionTool() {
    return new LocalSameParameterValueInspection(this);
  }

  private ProblemDescriptor registerProblem(@NotNull InspectionManager manager,
                                            PsiParameter parameter,
                                            Object value,
                                            boolean usedForWriting) {
    final String name = parameter.getName();
    String shortName;
    String stringPresentation;
    boolean accessible = true;
    if (value instanceof PsiType) {
      stringPresentation = ((PsiType)value).getCanonicalText() + ".class";
      shortName = ((PsiType)value).getPresentableText() + ".class";
    }
    else {
      if (value instanceof PsiField) {
        accessible = PsiUtil.isMemberAccessibleAt((PsiMember)value, parameter);
        stringPresentation = PsiFormatUtil.formatVariable((PsiVariable)value,
                                                          PsiFormatUtilBase.SHOW_NAME | PsiFormatUtilBase.SHOW_CONTAINING_CLASS | PsiFormatUtilBase.SHOW_FQ_NAME,
                                                          PsiSubstitutor.EMPTY);
        shortName = PsiFormatUtil.formatVariable((PsiVariable)value, 
                                                 PsiFormatUtilBase.SHOW_NAME | PsiFormatUtilBase.SHOW_CONTAINING_CLASS, 
                                                 PsiSubstitutor.EMPTY);
      }
      else {
        stringPresentation = shortName =  String.valueOf(value);
      }
    }
    return manager.createProblemDescriptor(ObjectUtils.notNull(parameter.getNameIdentifier(), parameter),
                                           InspectionsBundle.message("inspection.same.parameter.problem.descriptor",
                                                                     name,
                                                                     StringUtil.unquoteString(shortName)),
                                           usedForWriting || parameter.isVarArgs() || !accessible ? null : createFix(name, stringPresentation),
                                           ProblemHighlightType.GENERIC_ERROR_OR_WARNING, false);
  }

  public static class InlineParameterValueFix implements LocalQuickFix {
    private final String myValue;
    private final String myParameterName;

    private InlineParameterValueFix(String parameterName, String value) {
      myValue = value;
      myParameterName = parameterName;
    }

    @Override
    public String toString() {
      return getParamName() + " " + myValue;
    }

    @Override
    @NotNull
    public String getName() {
      return InspectionsBundle
        .message("inspection.same.parameter.fix.name", myParameterName, StringUtil.unquoteString(myValue));
    }

    @Override
    @NotNull
    public String getFamilyName() {
      return InspectionsBundle.message("inspection.same.parameter.fix.family.name");
    }

    @Override
    public void applyFix(@NotNull final Project project, @NotNull ProblemDescriptor descriptor) {
      final PsiElement element = descriptor.getPsiElement();
      final PsiMethod method = PsiTreeUtil.getParentOfType(element, PsiMethod.class);
      if (method == null) return;
      PsiParameter parameter = PsiTreeUtil.getParentOfType(element, PsiParameter.class, false);
      if (parameter == null) {
        final PsiParameter[] parameters = method.getParameterList().getParameters();
        for (PsiParameter psiParameter : parameters) {
          if (Comparing.strEqual(psiParameter.getName(), myParameterName)) {
            parameter = psiParameter;
            break;
          }
        }
      }
      if (parameter == null) return;
     

      final PsiExpression defToInline;
      try {
        defToInline = JavaPsiFacade.getInstance(project).getElementFactory()
                                   .createExpressionFromText(myValue, parameter);
      }
      catch (IncorrectOperationException e) {
        return;
      }
      final PsiParameter parameterToInline = parameter;
      inlineSameParameterValue(method, parameterToInline, defToInline);
    }

    @Override
    public boolean startInWriteAction() {
      return false;
    }

    public static void inlineSameParameterValue(final PsiMethod method, final PsiParameter parameter, final PsiExpression defToInline) {
      final MultiMap<PsiElement, String> conflicts = new MultiMap<>();
      Collection<PsiMethod> methods = new ArrayList<>();
      methods.add(method);
      Project project = method.getProject();
      if (!ProgressManager.getInstance()
        .runProcessWithProgressSynchronously(() -> { methods.addAll(OverridingMethodsSearch.search(method).findAll()); },
                                             "Search for Overriding Methods...", true, project)) {
        return;
      }
      if (!CommonRefactoringUtil.checkReadOnlyStatus(project, methods, true)) return;

      int parameterIndex = method.getParameterList().getParameterIndex(parameter);
      Map<PsiParameter, Collection<PsiReference>> paramsToInline = new HashMap<>();
      for (PsiMethod psiMethod : methods) {
        PsiParameter psiParameter = psiMethod.getParameterList().getParameters()[parameterIndex];
        JavaSafeDeleteProcessor.collectMethodConflicts(conflicts, psiMethod, psiParameter);
        final Collection<PsiReference> refsToInline = ReferencesSearch.search(psiParameter).findAll();
        for (PsiReference reference : refsToInline) {
          PsiElement referenceElement = reference.getElement();
          if (referenceElement instanceof PsiExpression && PsiUtil.isAccessedForWriting((PsiExpression)referenceElement)) {
            conflicts.putValue(referenceElement, "Parameter has write usages. Inline is not supported");
            break;
          }
        }
        paramsToInline.put(psiParameter, refsToInline);
      }
      if (!conflicts.isEmpty()) {
        if (ApplicationManager.getApplication().isUnitTestMode()) {
          if (!BaseRefactoringProcessor.ConflictsInTestsException.isTestIgnore()) {
            throw new BaseRefactoringProcessor.ConflictsInTestsException(conflicts.values());
          }
        }
        else if (!new ConflictsDialog(project, conflicts).showAndGet()) {
          return;
        }
      }

      ApplicationManager.getApplication().runWriteAction(() -> {
        for (Map.Entry<PsiParameter, Collection<PsiReference>> entry : paramsToInline.entrySet()) {
          Collection<PsiReference> refsToInline = entry.getValue();
          try {
            PsiExpression[] exprs = new PsiExpression[refsToInline.size()];
            int idx = 0;
            for (PsiReference reference : refsToInline) {
              if (reference instanceof PsiJavaCodeReferenceElement) {
                exprs[idx++] = InlineUtil.inlineVariable(entry.getKey(), defToInline, (PsiJavaCodeReferenceElement)reference);
              }
            }

            for (final PsiExpression expr : exprs) {
              if (expr != null) InlineUtil.tryToInlineArrayCreationForVarargs(expr);
            }
          }
          catch (IncorrectOperationException e) {
            LOG.error(e);
          }
        }
      });

      removeParameter(method, parameter);
    }

    public static void removeParameter(final PsiMethod method, final PsiParameter parameter) {
      final PsiParameter[] parameters = method.getParameterList().getParameters();
      final List<ParameterInfoImpl> psiParameters = new ArrayList<>();
      int paramIdx = 0;
      final String paramName = parameter.getName();
      for (PsiParameter param : parameters) {
        if (!Comparing.strEqual(paramName, param.getName())) {
          psiParameters.add(new ParameterInfoImpl(paramIdx, param.getName(), param.getType()));
        }
        paramIdx++;
      }

      new ChangeSignatureProcessor(method.getProject(), method, false, null, method.getName(), method.getReturnType(),
                                   psiParameters.toArray(new ParameterInfoImpl[0])).run();
    }

    public String getParamName() {
      return myParameterName;
    }
  }

  private class LocalSameParameterValueInspection extends AbstractBaseJavaLocalInspectionTool {
    private final SameParameterValueInspection myGlobal;

    private LocalSameParameterValueInspection(SameParameterValueInspection global) {
      myGlobal = global;
    }

    @Override
    public boolean runForWholeFile() {
      return true;
    }

    @Override
    @NotNull
    public String getGroupDisplayName() {
      return myGlobal.getGroupDisplayName();
    }

    @Override
    @NotNull
    public String getDisplayName() {
      return myGlobal.getDisplayName();
    }

    @Override
    @NotNull
    public String getShortName() {
      return myGlobal.getShortName();
    }

    @NotNull
    @Override
    public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder,
                                          boolean isOnTheFly,
                                          @NotNull LocalInspectionToolSession session) {
      return new JavaElementVisitor() {
        private final UnusedDeclarationInspectionBase
          myDeadCodeTool = UnusedDeclarationInspectionBase.findUnusedDeclarationInspection(holder.getFile());

        @Override
        public void visitMethod(PsiMethod method) {
          if (method.isConstructor() || VisibilityUtil
                                          .compare(VisibilityUtil.getVisibilityModifier(method.getModifierList()), highestModifier) < 0) return;

          if (method.hasModifierProperty(PsiModifier.NATIVE)) return;

          PsiParameter[] parameters = method.getParameterList().getParameters();
          if (parameters.length == 0) return;

          if (myDeadCodeTool.isEntryPoint(method)) return;
          if (!method.getHierarchicalMethodSignature().getSuperSignatures().isEmpty()) return;

          PsiParameter lastParameter = parameters[parameters.length - 1];
          final Object[] paramValues;
          final boolean hasVarArg = lastParameter.getType() instanceof PsiEllipsisType;
          if (hasVarArg) {
            if (parameters.length == 1) return;
            paramValues = new Object[parameters.length - 1];
          } else {
            paramValues = new Object[parameters.length];
          }
          Arrays.fill(paramValues, VALUE_UNDEFINED);

          if (UnusedSymbolUtil
            .processUsages(holder.getProject(), method.getContainingFile(), method, new EmptyProgressIndicator(), null, info -> {
            PsiElement element = info.getElement();

            if (!(element instanceof PsiReferenceExpression)) {
              return false;
            }
            PsiElement parent = element.getParent();
            if (!(parent instanceof PsiMethodCallExpression)) {
              return false;
            }
            PsiMethodCallExpression methodCall = (PsiMethodCallExpression) parent;
            PsiExpression[] arguments = methodCall.getArgumentList().getExpressions();
            if (arguments.length < paramValues.length) return false;

            boolean needFurtherProcess = false;
            for (int i = 0; i < paramValues.length; i++) {
              Object value = paramValues[i];
              final Object currentArg = getArgValue(arguments[i], method);
              if (value == VALUE_UNDEFINED) {
                paramValues[i] = currentArg;
                if (currentArg != VALUE_IS_NOT_CONST) {
                  needFurtherProcess = true;
                }
              } else if (value != VALUE_IS_NOT_CONST) {
                if (!Comparing.equal(paramValues[i], currentArg)) {
                  paramValues[i] = VALUE_IS_NOT_CONST;
                } else {
                  needFurtherProcess = true;
                }
              }
            }

            return needFurtherProcess;
          })) {
            for (int i = 0, length = paramValues.length; i < length; i++) {
              Object value = paramValues[i];
              if (value != VALUE_UNDEFINED && value != VALUE_IS_NOT_CONST) {
                holder.registerProblem(registerProblem(holder.getManager(), parameters[i], value, false));
              }
            }
          }
        }
      };
    }

    private Object getArgValue(PsiExpression arg, PsiMethod method) {
      return RefParameterImpl.getAccessibleExpressionValue(arg, () -> method);
    }
  }
}
