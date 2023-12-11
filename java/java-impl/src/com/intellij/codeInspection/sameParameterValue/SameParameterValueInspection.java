// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.sameParameterValue;

import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInsight.daemon.impl.UnusedSymbolUtil;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.codeInsight.intention.preview.IntentionPreviewUtils;
import com.intellij.codeInsight.options.JavaInspectionControls;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.deadCode.UnusedDeclarationInspectionBase;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.codeInspection.reference.*;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.OverridingMethodsSearch;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.*;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.JavaRefactoringFactory;
import com.intellij.refactoring.changeSignature.ParameterInfoImpl;
import com.intellij.refactoring.util.CommonJavaInlineUtil;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.refactoring.util.RefactoringConflictsUtil;
import com.intellij.uast.UastHintedVisitorAdapter;
import com.intellij.util.CommonJavaRefactoringUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ObjectUtils;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.*;
import org.jetbrains.uast.visitor.AbstractUastNonRecursiveVisitor;

import java.util.*;

import static com.intellij.codeInspection.options.OptPane.*;
import static com.intellij.codeInspection.reference.RefParameter.VALUE_IS_NOT_CONST;
import static com.intellij.codeInspection.reference.RefParameter.VALUE_UNDEFINED;
import static com.intellij.openapi.util.NlsContexts.DialogMessage;

public final class SameParameterValueInspection extends GlobalJavaBatchInspectionTool {
  private static final Logger LOG = Logger.getInstance(SameParameterValueInspection.class);
  public static final AccessModifier DEFAULT_HIGHEST_MODIFIER = AccessModifier.PROTECTED;
  public AccessModifier highestModifier = DEFAULT_HIGHEST_MODIFIER;
  public int minimalUsageCount = 1;
  public boolean ignoreWhenRefactoringIsComplicated = true;

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(
      checkbox("ignoreWhenRefactoringIsComplicated", JavaBundle.message("label.ignore.complicated.fix")),
      JavaInspectionControls.visibilityChooser("highestModifier", JavaBundle.message("label.maximal.reported.method.visibility")),
      number("minimalUsageCount", JavaBundle.message("label.minimal.reported.method.usage.count"), 1, 100)
    );
  }

  @Override
  public CommonProblemDescriptor @Nullable [] checkElement(@NotNull RefEntity refEntity,
                                                           @NotNull AnalysisScope scope,
                                                           @NotNull InspectionManager manager,
                                                           @NotNull GlobalInspectionContext globalContext,
                                                           @NotNull ProblemDescriptionsProcessor processor) {
    List<ProblemDescriptor> problems = null;
    if (refEntity instanceof RefMethod refMethod) {

      if (refMethod.hasSuperMethods() ||
          Objects.requireNonNull(AccessModifier.fromPsiModifier(refMethod.getAccessModifier())).compareTo(
            Objects.requireNonNullElse(highestModifier, DEFAULT_HIGHEST_MODIFIER)) < 0 ||
          refMethod.isEntry()) {
        return null;
      }

      RefParameter[] parameters = refMethod.getParameters();
      for (RefParameter refParameter : parameters) {
        Object value = refParameter.getActualConstValue();
        List<Object> valueList = valueToList(value);
        if (valueList == null || ContainerUtil.all(valueList, v -> v != VALUE_UNDEFINED && v != VALUE_IS_NOT_CONST)) {
          if (minimalUsageCount != 0 && refParameter.getUsageCount() < minimalUsageCount) continue;
          if (!globalContext.shouldCheck(refParameter, this)) continue;
          if (problems == null) problems = new ArrayList<>(1);
          UParameter parameter = refParameter.getUastElement();
          if (parameter == null) continue;
          Boolean isFixAvailable = isFixAvailable(parameter, value, refParameter.isUsedForWriting());
          if (Boolean.FALSE.equals(isFixAvailable) && ignoreWhenRefactoringIsComplicated) return null;
          Object presentableValue = value;
          if (valueList != null && valueList.size() == 1 && valueList.get(0) == null) presentableValue = valueList.get(0);
          ContainerUtil.addIfNotNull(problems, registerProblem(manager, parameter, presentableValue, Boolean.TRUE.equals(isFixAvailable)));
        }
      }
    }

    return problems == null ? null : problems.toArray(CommonProblemDescriptor.EMPTY_ARRAY);
  }

  @Nullable
  @Contract("null -> null; !null -> !null")
  private static List<Object> valueToList(@Nullable Object rootValue) {
    //noinspection unchecked
    return rootValue == null || rootValue instanceof List<?> ? (List<Object>)rootValue : new SmartList<>(rootValue);
  }

  @Override
  protected boolean queryExternalUsagesRequests(@NotNull RefManager manager, @NotNull GlobalJavaInspectionContext globalContext,
                                                @NotNull ProblemDescriptionsProcessor processor) {
    manager.iterate(new RefJavaVisitor() {
      @Override
      public void visitMethod(@NotNull RefMethod refMethod) {
        if (processor.getDescriptions(refMethod) == null) return;
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
  public LocalQuickFix getQuickFix(String hint) {
    if (hint == null) return null;
    final int spaceIdx = hint.indexOf(' ');
    if (spaceIdx == -1 || spaceIdx >= hint.length() - 1) return null; //invalid hint
    return new InlineParameterValueFix(hint.substring(0, spaceIdx), hint.substring(spaceIdx + 1));
  }

  @Override
  @Nullable
  public String getHint(@NotNull QuickFix fix) {
    return fix.toString();
  }

  @Nullable
  @Override
  public LocalInspectionTool getSharedLocalInspectionTool() {
    return new LocalSameParameterValueInspection(this);
  }

  private static @Nullable ProblemDescriptor registerProblem(@NotNull InspectionManager manager,
                                                             @NotNull UParameter parameter,
                                                             Object value,
                                                             boolean suggestFix) {
    final String name = parameter.getName();
    if (name == null || name.isEmpty()) return null;
    String presentableText;
    String canonicalText;
    if (value instanceof PsiType) {
      canonicalText = ((PsiType)value).getCanonicalText() + ".class";
      presentableText = ((PsiType)value).getPresentableText() + ".class";
    }
    else {
      if (value instanceof PsiField) {
        canonicalText = PsiFormatUtil.formatVariable((PsiVariable)value,
                                                     PsiFormatUtilBase.SHOW_NAME | PsiFormatUtilBase.SHOW_CONTAINING_CLASS | PsiFormatUtilBase.SHOW_FQ_NAME,
                                                     PsiSubstitutor.EMPTY);
        presentableText = PsiFormatUtil.formatVariable((PsiVariable)value,
                                                       PsiFormatUtilBase.SHOW_NAME | PsiFormatUtilBase.SHOW_CONTAINING_CLASS,
                                                       PsiSubstitutor.EMPTY);
      }
      else {
        canonicalText = presentableText = String.valueOf(value);
      }
    }
    PsiElement anchor = ObjectUtils.notNull(UDeclarationKt.getAnchorPsi(parameter), parameter);
    if (!anchor.isPhysical()) return null;
    return manager.createProblemDescriptor(anchor,
                                           JavaBundle.message("inspection.same.parameter.problem.descriptor",
                                                              StringUtil.unquoteString(presentableText)),
                                           suggestFix ? new InlineParameterValueFix(name, canonicalText) : null,
                                           ProblemHighlightType.GENERIC_ERROR_OR_WARNING, false);
  }

  private static @Nullable Boolean isFixAvailable(UParameter parameter, Object value, boolean usedForWriting) {
    if (usedForWriting) return false;
    PsiParameter javaParameter = ObjectUtils.tryCast(parameter.getSourcePsi(), PsiParameter.class);
    if (javaParameter == null) return null;
    if (javaParameter.isVarArgs()) return false;
    return !(value instanceof PsiField) || PsiUtil.isMemberAccessibleAt((PsiMember)value, javaParameter);
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
      return JavaBundle.message("inspection.same.parameter.fix.name", myParameterName, StringUtil.unquoteString(myValue));
    }

    @Override
    @NotNull
    public String getFamilyName() {
      return JavaBundle.message("inspection.same.parameter.fix.family.name");
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      final PsiElement element = descriptor.getPsiElement();
      final PsiMethod method = PsiTreeUtil.getParentOfType(element, PsiMethod.class);
      if (method == null) return;
      PsiParameter parameter = PsiTreeUtil.getParentOfType(element, PsiParameter.class, false);
      if (parameter == null) {
        parameter =
          ContainerUtil.find(method.getParameterList().getParameters(), (param) -> Comparing.strEqual(param.getName(), myParameterName));
      }
      if (parameter == null) return;
      final PsiExpression expression = JavaPsiFacade.getElementFactory(project).createExpressionFromText(myValue, parameter);
      inlineSameParameterValue(method, parameter, expression);
    }

    @Override
    public @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project, @NotNull ProblemDescriptor previewDescriptor) {
      applyFix(project, previewDescriptor);
      return IntentionPreviewInfo.DIFF;
    }

    @Override
    public boolean startInWriteAction() {
      return false;
    }

    public static void inlineSameParameterValue(PsiMethod method, PsiParameter parameter, PsiExpression defToInline) {
      final MultiMap<PsiElement, @DialogMessage String> conflicts = new MultiMap<>();
      Collection<PsiMethod> methods = new ArrayList<>();
      methods.add(method);
      Project project = method.getProject();
      boolean preview = IntentionPreviewUtils.isIntentionPreviewActive();
      if (!preview) {
        if (!ProgressManager.getInstance().runProcessWithProgressSynchronously(
          () -> {
            methods.addAll(OverridingMethodsSearch.search(method).findAll());
          },
          JavaBundle.message("progress.title.search.for.overriding.methods"), true, project)) {
          return;
        }
        if (!CommonRefactoringUtil.checkReadOnlyStatus(project, methods, true)) return;
      }

      int parameterIndex = method.getParameterList().getParameterIndex(parameter);
      Map<PsiParameter, Collection<PsiReference>> paramsToInline = new HashMap<>();
      for (PsiMethod psiMethod : methods) {
        PsiParameter psiParameter = psiMethod.getParameterList().getParameters()[parameterIndex];
        RefactoringConflictsUtil.getInstance().analyzeMethodConflictsAfterParameterDelete(psiMethod, psiParameter, conflicts);
        final Collection<PsiReference> refsToInline = ReferencesSearch.search(psiParameter).findAll();
        for (PsiReference reference : refsToInline) {
          PsiElement referenceElement = reference.getElement();
          if (referenceElement instanceof PsiExpression && PsiUtil.isAccessedForWriting((PsiExpression)referenceElement)) {
            conflicts.putValue(referenceElement, JavaBundle.message("dialog.message.parameter.has.write.usages.inline.not.supported"));
            break;
          }
        }
        paramsToInline.put(psiParameter, refsToInline);
      }
      if (!BaseRefactoringProcessor.processConflicts(project, conflicts)) return;

      if (preview) {
        inlineParameters(defToInline, paramsToInline);
        final boolean vararg = parameter.isVarArgs();
        parameter.delete();
        Collection<PsiReference> calls = ReferencesSearch.search(method, new LocalSearchScope(method.getContainingFile())).findAll();
        for (PsiReference call : calls) {
          PsiElement parent = call.getElement().getParent();
          if (parent instanceof PsiMethodCallExpression methodCallExpression) {
            PsiExpression[] arguments = methodCallExpression.getArgumentList().getExpressions();
            if (vararg) {
              methodCallExpression.deleteChildRange(arguments[parameterIndex], arguments[arguments.length - 1]);
            }
            else {
              arguments[parameterIndex].delete();
            }
          }
        }
      }
      else {
        WriteAction.run(() -> inlineParameters(defToInline, paramsToInline));
        removeParameter(method, parameter);
      }
    }

    private static void inlineParameters(PsiExpression defToInline, Map<PsiParameter, Collection<PsiReference>> paramsToInline) {
      for (Map.Entry<PsiParameter, Collection<PsiReference>> entry : paramsToInline.entrySet()) {
        Collection<PsiReference> refsToInline = entry.getValue();
        try {
          PsiExpression[] exprs = new PsiExpression[refsToInline.size()];
          int idx = 0;
          for (PsiReference reference : refsToInline) {
            if (reference instanceof PsiJavaCodeReferenceElement) {
              exprs[idx++] = CommonJavaInlineUtil.getInstance().inlineVariable(entry.getKey(), defToInline, (PsiJavaCodeReferenceElement)reference, null);
            }
          }

          for (PsiExpression expr : exprs) {
            if (expr != null) CommonJavaRefactoringUtil.tryToInlineArrayCreationForVarargs(expr);
          }
        }
        catch (IncorrectOperationException e) {
          LOG.error(e);
        }
      }
    }

    public static void removeParameter(PsiMethod method, PsiParameter parameter) {
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

      ParameterInfoImpl @NotNull [] parameterInfo = psiParameters.toArray(new ParameterInfoImpl[0]);
      var processor = JavaRefactoringFactory.getInstance(method.getProject())
        .createChangeSignatureProcessor(method, false, null, method.getName(), method.getReturnType(), parameterInfo, null, null, null,
                                        null);
      processor.run();
    }

    public String getParamName() {
      return myParameterName;
    }
  }

  private static final class LocalSameParameterValueInspection extends AbstractBaseUastLocalInspectionTool {
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
          if (method.isConstructor() ||
              AccessModifier.fromModifierList(javaMethod.getModifierList()).compareTo(
                Objects.requireNonNullElse(myGlobal.highestModifier, DEFAULT_HIGHEST_MODIFIER)) < 0) {
            return true;
          }

          if (javaMethod.hasModifierProperty(PsiModifier.NATIVE)) return true;

          List<UParameter> parameters = method.getUastParameters();
          if (parameters.isEmpty()) return true;

          if (myDeadCodeTool.isEntryPoint(javaMethod)) return true;
          if (!javaMethod.getHierarchicalMethodSignature().getSuperSignatures().isEmpty()) return true;

          Object[] paramValues = new Object[parameters.size()];
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
              if (!(parent instanceof UCallExpression methodCall)) {
                return false;
              }
              if (methodCall.getValueArguments().size() < paramValues.length) return false;

              boolean needFurtherProcess = false;
              for (int i = 0; i < paramValues.length; i++) {
                UExpression arg = methodCall.getArgumentForParameter(i);
                Object argValue = RefParameterImpl.getAccessibleExpressionValue(arg, () -> method.getSourcePsi());
                Object paramValue = paramValues[i];
                if (paramValue == VALUE_UNDEFINED) {
                  paramValues[i] = argValue;
                  if (argValue != VALUE_IS_NOT_CONST) {
                    needFurtherProcess = true;
                  }
                } else if (paramValue != VALUE_IS_NOT_CONST) {
                  if (!Comparing.equal(paramValue, argValue)) {
                    paramValues[i] = VALUE_IS_NOT_CONST;
                  } else {
                    needFurtherProcess = true;
                  }
                }
              }

              return needFurtherProcess;
            })) {
            if (myGlobal.minimalUsageCount != 0 && usageCount[0] < myGlobal.minimalUsageCount) return true;
            for (int i = 0, length = paramValues.length; i < length; i++) {
              Object value = paramValues[i];
              List<Object> valueList = valueToList(value);
              if (valueList == null || ContainerUtil.all(valueList, v -> v != VALUE_UNDEFINED && v != VALUE_IS_NOT_CONST)) {
                final UParameter parameter = parameters.get(i);
                Boolean isFixAvailable = isFixAvailable(parameter, value, false);
                if (Boolean.FALSE.equals(isFixAvailable) && myGlobal.ignoreWhenRefactoringIsComplicated) return true;
                Object presentableValue = value;
                if (valueList != null && valueList.size() == 1 && valueList.get(0) == null) presentableValue = valueList.get(0);
                ProblemDescriptor descriptor =
                  registerProblem(holder.getManager(), parameter, presentableValue, Boolean.TRUE.equals(isFixAvailable));
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
  }
}
