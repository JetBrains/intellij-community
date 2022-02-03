// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.sameParameterValue;

import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInsight.daemon.impl.UnusedSymbolUtil;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.deadCode.UnusedDeclarationInspectionBase;
import com.intellij.codeInspection.reference.*;
import com.intellij.codeInspection.ui.InspectionOptionsPanel;
import com.intellij.codeInspection.unusedSymbol.VisibilityModifierChooser;
import com.intellij.java.JavaBundle;
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
import com.intellij.refactoring.JavaSpecialRefactoringProvider;
import com.intellij.refactoring.changeSignature.ParameterInfoImpl;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.uast.UastHintedVisitorAdapter;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.fields.IntegerField;
import com.intellij.ui.components.fields.valueEditors.ValueEditor;
import com.intellij.util.CommonJavaRefactoringUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ObjectUtils;
import com.intellij.util.VisibilityUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.*;
import org.jetbrains.uast.visitor.AbstractUastNonRecursiveVisitor;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.*;

import static com.intellij.codeInspection.reference.RefParameter.VALUE_IS_NOT_CONST;
import static com.intellij.codeInspection.reference.RefParameter.VALUE_UNDEFINED;

public class SameParameterValueInspection extends GlobalJavaBatchInspectionTool {
  private static final Logger LOG = Logger.getInstance(SameParameterValueInspection.class);
  @PsiModifier.ModifierConstant
  private static final String DEFAULT_HIGHEST_MODIFIER = PsiModifier.PROTECTED;
  @PsiModifier.ModifierConstant
  public String highestModifier = DEFAULT_HIGHEST_MODIFIER;
  public int minimalUsageCount = 1;
  public boolean ignoreWhenRefactoringIsComplicated = true;

  @Nullable
  @Override
  public JComponent createOptionsPanel() {
    JPanel panel = new InspectionOptionsPanel();

    final JBCheckBox checkBox = new JBCheckBox(JavaBundle.message("label.ignore.complicated.fix"), ignoreWhenRefactoringIsComplicated);
    checkBox.addChangeListener((e) -> ignoreWhenRefactoringIsComplicated = checkBox.isSelected());
    panel.add(checkBox);

    LabeledComponent<VisibilityModifierChooser> component = LabeledComponent.create(new VisibilityModifierChooser(() -> true,
                                                                                                                  highestModifier,
                                                                                                                  (newModifier) -> highestModifier = newModifier),
                                                                                    JavaBundle
                                                                                      .message("label.maximal.reported.method.visibility"),
                                                                                    BorderLayout.WEST);
    panel.add(component);

    IntegerField minimalUsageCountEditor = new IntegerField(null, 1, Integer.MAX_VALUE);
    minimalUsageCountEditor.getValueEditor().addListener(new ValueEditor.Listener<>() {
      @Override
      public void valueChanged(@NotNull Integer newValue) {
        minimalUsageCount = newValue;
      }
    });
    minimalUsageCountEditor.setValue(minimalUsageCount);
    minimalUsageCountEditor.setColumns(4);
    panel.add(LabeledComponent.create(minimalUsageCountEditor, JavaBundle.message("label.minimal.reported.method.usage.count"), BorderLayout.WEST));
    return panel;
  }


  protected LocalQuickFix createFix(String paramName, String value) {
    return new InlineParameterValueFix(paramName, value);
  }

  @Override
  public CommonProblemDescriptor @Nullable [] checkElement(@NotNull RefEntity refEntity,
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
          if (minimalUsageCount != 0 && refParameter.getUsageCount() < minimalUsageCount) continue;
          if (!globalContext.shouldCheck(refParameter, this)) continue;
          if (problems == null) problems = new ArrayList<>(1);
          UParameter parameter = refParameter.getUastElement();
          if (parameter == null) continue;
          Boolean isFixAvailable = isFixAvailable(parameter, value, refParameter.isUsedForWriting());
          if (Boolean.FALSE.equals(isFixAvailable) && ignoreWhenRefactoringIsComplicated) return null;
          ContainerUtil.addIfNotNull(problems, registerProblem(manager, parameter, value, Boolean.TRUE.equals(isFixAvailable)));
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
              if (PsiModifier.PRIVATE.equals(refMethod.getAccessModifier())) return;
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
  public String getGroupDisplayName() {
    return InspectionsBundle.message("group.names.declaration.redundancy");
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

  private @Nullable ProblemDescriptor registerProblem(@NotNull InspectionManager manager,
                                                      @NotNull UParameter parameter,
                                                      Object value,
                                                      boolean suggestFix) {
    final String name = parameter.getName();
    if (name.isEmpty()) return null;
    String shortName;
    String stringPresentation;

    if (value instanceof PsiType) {
      stringPresentation = ((PsiType)value).getCanonicalText() + ".class";
      shortName = ((PsiType)value).getPresentableText() + ".class";
    }
    else {
      if (value instanceof PsiField) {
        stringPresentation = PsiFormatUtil.formatVariable((PsiVariable)value,
                                                          PsiFormatUtilBase.SHOW_NAME | PsiFormatUtilBase.SHOW_CONTAINING_CLASS | PsiFormatUtilBase.SHOW_FQ_NAME,
                                                          PsiSubstitutor.EMPTY);
        shortName = PsiFormatUtil.formatVariable((PsiVariable)value,
                                                 PsiFormatUtilBase.SHOW_NAME | PsiFormatUtilBase.SHOW_CONTAINING_CLASS,
                                                 PsiSubstitutor.EMPTY);
      }
      else if (value instanceof Character) {
        stringPresentation = shortName = "'" + value + "'";
      }
      else {
        stringPresentation = shortName = String.valueOf(value);
      }
    }
    PsiElement anchor = ObjectUtils.notNull(UDeclarationKt.getAnchorPsi(parameter), parameter);
    if (!anchor.isPhysical()) return null;
    return manager.createProblemDescriptor(anchor,
                                           JavaBundle.message("inspection.same.parameter.problem.descriptor",
                                                              name,
                                                              StringUtil.unquoteString(shortName)),
                                           suggestFix ? createFix(name, stringPresentation.startsWith("\"\"")
                                                                        ? stringPresentation
                                                                        : StringUtil.escapeLineBreak(stringPresentation)) : null,
                                           ProblemHighlightType.GENERIC_ERROR_OR_WARNING, false);
  }

  protected static @Nullable Boolean isFixAvailable(UParameter parameter, Object value, boolean usedForWriting) {
    if (usedForWriting) return false;
    PsiParameter javaParameter = ObjectUtils.tryCast(parameter.getSourcePsi(), PsiParameter.class);
    if (javaParameter == null) return null;
    if (value instanceof PsiField && !PsiUtil.isMemberAccessibleAt((PsiMember)value, javaParameter)) {
      return false;
    }
    return true;
  }

  public static final class InlineParameterValueFix implements LocalQuickFix {
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
      return JavaBundle
        .message("inspection.same.parameter.fix.name", myParameterName, StringUtil.unquoteString(myValue));
    }

    @Override
    @NotNull
    public String getFamilyName() {
      return JavaBundle.message("inspection.same.parameter.fix.family.name");
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
        defToInline = JavaPsiFacade.getElementFactory(project)
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
                                             JavaBundle.message("progress.title.search.for.overriding.methods"), true, project)) {
        return;
      }
      if (!CommonRefactoringUtil.checkReadOnlyStatus(project, methods, true)) return;

      int parameterIndex = method.getParameterList().getParameterIndex(parameter);
      Map<PsiParameter, Collection<PsiReference>> paramsToInline = new HashMap<>();
      for (PsiMethod psiMethod : methods) {
        PsiParameter psiParameter = psiMethod.getParameterList().getParameters()[parameterIndex];
        JavaSpecialRefactoringProvider.getInstance().collectMethodConflicts(conflicts, psiMethod, psiParameter);
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
      if (!BaseRefactoringProcessor.processConflicts(project, conflicts)) return;

      ApplicationManager.getApplication().runWriteAction(() -> {
        for (Map.Entry<PsiParameter, Collection<PsiReference>> entry : paramsToInline.entrySet()) {
          Collection<PsiReference> refsToInline = entry.getValue();
          try {
            PsiExpression[] exprs = new PsiExpression[refsToInline.size()];
            int idx = 0;
            for (PsiReference reference : refsToInline) {
              if (reference instanceof PsiJavaCodeReferenceElement) {
                exprs[idx++] = JavaSpecialRefactoringProvider.getInstance().inlineVariable(entry.getKey(), defToInline, (PsiJavaCodeReferenceElement)reference, null);
              }
            }

            for (final PsiExpression expr : exprs) {
              if (expr != null) CommonJavaRefactoringUtil.tryToInlineArrayCreationForVarargs(expr);
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
          psiParameters.add(ParameterInfoImpl.create(paramIdx).withName(param.getName()).withType(param.getType()));
        }
        paramIdx++;
      }

      var provider = JavaSpecialRefactoringProvider.getInstance();
      var processor = provider.getChangeSignatureProcessorWithCallback(
        method.getProject(), method, false, null, method.getName(), method.getReturnType(),
        psiParameters.toArray(new ParameterInfoImpl[0]), true, null
      );
      processor.run();
    }

    public String getParamName() {
      return myParameterName;
    }
  }

  private final class LocalSameParameterValueInspection extends AbstractBaseUastLocalInspectionTool {
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
    public String getShortName() {
      return myGlobal.getShortName();
    }

    @NotNull
    @Override
    public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
      return UastHintedVisitorAdapter.create(holder.getFile().getLanguage(), new AbstractUastNonRecursiveVisitor() {
        private final UnusedDeclarationInspectionBase
          myDeadCodeTool = UnusedDeclarationInspectionBase.findUnusedDeclarationInspection(holder.getFile());

        @Override
        public boolean visitMethod(@NotNull UMethod method) {
          PsiMethod javaMethod = method.getJavaPsi();
          if (method.isConstructor() || VisibilityUtil.compare(VisibilityUtil.getVisibilityModifier(javaMethod.getModifierList()), highestModifier) < 0) return true;

          if (javaMethod.hasModifierProperty(PsiModifier.NATIVE)) return true;

          List<UParameter> parameters = method.getUastParameters();
          if (parameters.isEmpty()) return true;

          if (myDeadCodeTool.isEntryPoint(javaMethod)) return true;
          if (!javaMethod.getHierarchicalMethodSignature().getSuperSignatures().isEmpty()) return true;

          UParameter lastParameter = parameters.get(parameters.size() - 1);
          final Object[] paramValues;
          final boolean hasVarArg = lastParameter.getType() instanceof PsiEllipsisType;
          if (hasVarArg) {
            if (parameters.size() == 1) return true;
            paramValues = new Object[parameters.size() - 1];
          } else {
            paramValues = new Object[parameters.size()];
          }
          Arrays.fill(paramValues, VALUE_UNDEFINED);

          int[] usageCount = {0};
          if (UnusedSymbolUtil.processUsages(holder.getProject(), holder.getFile(), javaMethod, new EmptyProgressIndicator(), null, info -> {
              PsiElement element = info.getElement();
              usageCount[0]++;
              UElement uElement = UastContextKt.toUElement(element);
              if (!(uElement instanceof UReferenceExpression)) {
                return false;
              }
              if (uElement instanceof UCallableReferenceExpression) {
                return false;
              }
              UElement parent = uElement.getUastParent();
              if (!(parent instanceof UCallExpression)) {
                return false;
              }
              UCallExpression methodCall = (UCallExpression) parent;
              List<UExpression> arguments = methodCall.getValueArguments();
              if (arguments.size() < paramValues.length) return false;

              boolean needFurtherProcess = false;
              for (int i = 0; i < paramValues.length; i++) {
                Object value = paramValues[i];
                final Object currentArg = getArgValue(arguments.get(i), method.getPsi());
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
            if (minimalUsageCount != 0 && usageCount[0] < minimalUsageCount) return true;
            for (int i = 0, length = paramValues.length; i < length; i++) {
              Object value = paramValues[i];
              if (value != VALUE_UNDEFINED && value != VALUE_IS_NOT_CONST) {
                final UParameter parameter = parameters.get(i);
                Boolean isFixAvailable = isFixAvailable(parameter, value, false);
                if (Boolean.FALSE.equals(isFixAvailable) && ignoreWhenRefactoringIsComplicated) return true;
                ProblemDescriptor descriptor = registerProblem(holder.getManager(), parameter, value, Boolean.TRUE.equals(isFixAvailable));
                if (descriptor != null) {
                  holder.registerProblem(descriptor);
                }
              }
            }
          }
          return true;
        }
      }, new Class[]{UMethod.class});
    }

    private Object getArgValue(UExpression arg, PsiMethod method) {
      return RefParameterImpl.getAccessibleExpressionValue(arg, () -> method);
    }
  }
}
