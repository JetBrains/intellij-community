// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.style;

import com.intellij.codeInspection.*;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.ide.util.SuperMethodWarningUtil;
import com.intellij.java.JavaBundle;
import com.intellij.java.codeserver.highlighting.JavaErrorCollector;
import com.intellij.java.syntax.parser.JavaKeywords;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.*;
import com.intellij.psi.impl.DebugUtil;
import com.intellij.psi.impl.source.DummyHolderFactory;
import com.intellij.psi.impl.source.JavaDummyHolder;
import com.intellij.psi.impl.source.PsiClassReferenceType;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.OverridingMethodsSearch;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.search.searches.SuperMethodsSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.changeSignature.JavaChangeSignatureDialog;
import com.intellij.refactoring.changeSignature.ParameterInfoImpl;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.InspectionGadgetsBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;

import static com.intellij.codeInspection.options.OptPane.checkbox;
import static com.intellij.codeInspection.options.OptPane.pane;

/**
 * {@code "void process(Processor<T> p)"  -> "void process(Processor<? super T> p)"}
 */
public final class BoundedWildcardInspection extends AbstractBaseJavaLocalInspectionTool {
  private static final Logger LOG = Logger.getInstance(BoundedWildcardInspection.class);
  @SuppressWarnings("WeakerAccess") public boolean REPORT_INVARIANT_CLASSES = true;
  @SuppressWarnings("WeakerAccess") public boolean REPORT_PRIVATE_METHODS = true;
  @SuppressWarnings("WeakerAccess") public boolean REPORT_INSTANCE_METHODS = true;

  @Override
  public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly, @NotNull LocalInspectionToolSession session) {
    return new JavaElementVisitor() {
      @Override
      public void visitTypeElement(@NotNull PsiTypeElement typeElement) {
        VarianceCandidate candidate = VarianceCandidate.findVarianceCandidate(typeElement);
        if (candidate == null) return;
        PsiTypeParameterListOwner owner = candidate.typeParameter.getOwner();
        if (owner instanceof PsiClass && !REPORT_INVARIANT_CLASSES && VarianceUtil.getClassVariance((PsiClass)owner, candidate.typeParameter) == Variance.INVARIANT) {
          return; // Nikolay despises List<? extends T>
        }
        PsiClass containingClass = candidate.method.getContainingClass();
        if (!REPORT_PRIVATE_METHODS && (candidate.method.hasModifierProperty(PsiModifier.PRIVATE)
                                        // methods of private class considered private for the purpose of this inspection
                                        || containingClass != null && containingClass.hasModifierProperty(PsiModifier.PRIVATE))) {
          return; // somebody hates his precious private methods highlighted
        }
        if (!REPORT_INSTANCE_METHODS &&
            !candidate.method.hasModifierProperty(PsiModifier.STATIC) &&
            !candidate.method.isConstructor()) {
          return; // somebody doesn't want to report instance methods highlighted because they can be already overridden
        }
        Project project = holder.getProject();
        boolean canBeSuper = canChangeTo(project, candidate, false);
        boolean canBeExtends = canChangeTo(project, candidate, true);
        if (canBeExtends == canBeSuper || VarianceUtil.areBoundsSaturated(candidate, canBeExtends)) return;

        boolean wildCardIsUseless = VarianceUtil.wildCardIsUseless(candidate, canBeExtends);
        ProblemHighlightType type = wildCardIsUseless ? ProblemHighlightType.WEAK_WARNING : ProblemHighlightType.GENERIC_ERROR_OR_WARNING;
        String msg = canBeExtends
                      ? InspectionGadgetsBundle.message("bounded.wildcard.covariant.descriptor")
                      : InspectionGadgetsBundle.message("bounded.wildcard.contravariant.descriptor");
        // show verbose message in debug mode only
        if (!wildCardIsUseless || LOG.isDebugEnabled()) {
          @NonNls String verboseDebugMessage = " but decided against it";
          holder.registerProblem(typeElement, msg + (wildCardIsUseless ? verboseDebugMessage : ""), type, new ReplaceWithQuestionTFix(isOverriddenOrOverrides(candidate.method), canBeExtends));
        }
      }
    };
  }


  private static class ReplaceWithQuestionTFix implements LocalQuickFix {
    private final boolean isOverriddenOrOverrides;
    private final boolean isExtends;

    ReplaceWithQuestionTFix(boolean isOverriddenOrOverrides, boolean isExtends) {
      this.isOverriddenOrOverrides = isOverriddenOrOverrides;
      this.isExtends = isExtends;
    }

    @Override
    public @Nls(capitalization = Nls.Capitalization.Sentence) @NotNull String getFamilyName() {
      return CommonQuickFixBundle.message("fix.replace.with.x", "? " + (isExtends ? JavaKeywords.EXTENDS : JavaKeywords.SUPER));
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor)  {
      PsiElement element = descriptor.getPsiElement();
      if (!(element instanceof PsiTypeElement typeElement) || !element.isValid() || element.getParent() == null || !element.isPhysical()) return;

      VarianceCandidate candidate = VarianceCandidate.findVarianceCandidate(typeElement);
      if (candidate == null) return;
      PsiMethod method = candidate.method;


      if (!isOverriddenOrOverrides) {
        PsiField field = findFieldAssignedFromMethodParameter(candidate.methodParameter, method);
        if (field != null) {
          replaceType(project, Objects.requireNonNull(field.getTypeElement()), suggestMethodParameterType(candidate, isExtends));
        }

        PsiTypeElement methodParameterTypeElement = Objects.requireNonNull(candidate.methodParameter.getTypeElement());
        PsiClassType clone = suggestMethodParameterType(candidate, isExtends);
        replaceType(project, methodParameterTypeElement, clone);
        return;
      }

      int index = method.getParameterList().getParameterIndex(candidate.methodParameter);
      if (index == -1) return;

      PsiMethod superMethod = SuperMethodWarningUtil.checkSuperMethod(method);
      if (superMethod == null) return;
      if (superMethod != method) {
        method = superMethod;
        candidate = candidate.getSuperMethodVarianceCandidate(superMethod);
      }
      PsiClassType clone = suggestMethodParameterType(candidate, isExtends);
      int[] i = {0};
      String candidateName = candidate.methodParameter.getName();
      List<ParameterInfoImpl> parameterInfos = ContainerUtil.map(superMethod.getParameterList().getParameters(),
                                                                 p -> {
                                                                   int i1 = i[0]++;
                                                                   return ParameterInfoImpl.create(i1)
                                                                     .withName(i1 == index ? candidateName : p.getName())
                                                                     .withType(i1 == index ? clone : p.getType());
                                                                 });
      JavaChangeSignatureDialog
        dialog = JavaChangeSignatureDialog.createAndPreselectNew(project, method, parameterInfos, false, null/*todo?*/);
      dialog.setParameterInfos(parameterInfos);
      if (dialog.showAndGet()) {
        PsiField field = findFieldAssignedFromMethodParameter(candidate.methodParameter, method);
        if (field != null) {
          replaceType(project, field.getTypeElement(), suggestMethodParameterType(candidate, isExtends));
        }
      }
    }

    private static void replaceType(@NotNull Project project, @NotNull PsiTypeElement typeElement, @NotNull PsiType withType) {
      PsiElementFactory pf = PsiElementFactory.getInstance(project);
      PsiTypeElement newTypeElement = pf.createTypeElement(withType);
      if (typeElement.isPhysical()) {
        WriteCommandAction.runWriteCommandAction(project, JavaBundle.message("command.name.replace.type"), null,
                                                 () -> typeElement.replace(newTypeElement),
                                                 typeElement.getContainingFile());
      }
      else {
        typeElement.replace(newTypeElement);
      }
    }

    @Override
    public boolean startInWriteAction() {
      return false;
    }
  }

  private static @NotNull PsiClassType suggestMethodParameterType(@NotNull VarianceCandidate candidate, boolean isExtends) {
    PsiType type = candidate.type;

    PsiManager psiManager = candidate.method.getManager();
    PsiElementFactory pf = PsiElementFactory.getInstance(psiManager.getProject());
    PsiTypeElement newInnerTypeElement = pf.createTypeElement(isExtends ? PsiWildcardType
      .createExtends(psiManager, type) : PsiWildcardType.createSuper(psiManager, type));

    PsiClassReferenceType methodParamType = candidate.methodParameterType;
    PsiClassReferenceType clone = new PsiClassReferenceType((PsiJavaCodeReferenceElement)methodParamType.getReference().copy(), methodParamType.getLanguageLevel());

    PsiJavaCodeReferenceElement cloneReference = clone.getReference();
    PsiTypeElement innerTypeElement = cloneReference.getParameterList().getTypeParameterElements()[candidate.typeParameterIndex];


    innerTypeElement.replace(newInnerTypeElement);
    return clone.annotate(methodParamType.getAnnotationProvider());
  }

  private static boolean isOverriddenOrOverrides(@NotNull PsiMethod method) {
    if (method.isConstructor() ||
        method.hasModifierProperty(PsiModifier.PRIVATE) ||
        method.hasModifierProperty(PsiModifier.STATIC)) return false;

    if (method.hasModifierProperty(PsiModifier.ABSTRACT)) return true;

    boolean overrides = SuperMethodsSearch.search(method, null, true, false).findFirst() != null;
    return overrides || OverridingMethodsSearch.search(method).findFirst() != null;
  }

  private static PsiField findFieldAssignedFromMethodParameter(@NotNull PsiParameter methodParameter, @NotNull PsiMethod method) {
    PsiCodeBlock methodBody = method.getBody();
    if (methodBody == null) return null;
    PsiClass containingClass = method.getContainingClass();
    Ref<Pair<PsiField, PsiType>> assignedToField = Ref.create();
    ReferencesSearch.search(methodParameter, new LocalSearchScope(methodBody)).forEach(ref -> {
      ProgressManager.checkCanceled();
      Pair<PsiField, PsiType> assigned = isAssignedToField(ref, containingClass);
      if (assigned != null) {
        if (!assignedToField.isNull() && !assigned.equals(assignedToField.get())) {
          assignedToField.set(null);
          return false;
        }
        assignedToField.set(assigned);
      }
      return true;
    });

    return Pair.getFirst(assignedToField.get());
  }

  private static boolean canChangeTo(@NotNull Project project, @NotNull VarianceCandidate candidate, boolean isExtends) {
    @NotNull PsiMethod method = candidate.method;
    PsiCodeBlock methodBody = method.getBody();
    if (methodBody == null) return false;

    PsiClassType newParameterType = suggestMethodParameterType(candidate, isExtends);

    PsiMethod methodCopy = DebugUtil
      .performPsiModification("Creating method copy", () -> createMethodCopy(project, candidate.method, candidate.methodParameterIndex, newParameterType));
    PsiClass containingClass = candidate.method.getContainingClass();
    PsiField field = findFieldAssignedFromMethodParameter(candidate.methodParameter, method);
    List<PsiElement> superMethodsCalls = new ArrayList<>(); // shouldn't error-check these because they're not generalized yet
    findSuperMethodCallsInside(methodCopy, candidate.superMethods, superMethodsCalls);
    // for same-named methods we have to copy the class because the specific method would not be resolved otherwise
    if (field == null && containingClass.findMethodsByName(candidate.method.getName()).length == 1) {
      // check body only to avoid messing with @Override annotations errors
      return errorChecks(methodCopy.getBody(), superMethodsCalls);
    }
    // not anonymous nor local
    if (containingClass.getQualifiedName() != null) {
      // field can be referenced from anywhere in the file
      PsiMethod methodCopyInClass = DebugUtil.performPsiModification("Creating class copy",
                                    () -> createClassCopy(project, field, containingClass, candidate.method, methodCopy, newParameterType));
      Collection<PsiMethod> methodsToErrorCheck;

      // check all field usages in the file
      if (field != null) {
        PsiClass classCopy = methodCopyInClass.getContainingClass();
        int fieldIndex = ArrayUtil.indexOf(containingClass.getFields(), field);
        if (fieldIndex == -1) return false;
        PsiField fieldCopy = classCopy.getFields()[fieldIndex];
        Collection<PsiReference> refs = ReferencesSearch.search(fieldCopy, new LocalSearchScope(classCopy)).findAll();
        Map<PsiMethod, List<PsiReference>> collect =
          refs.stream()
            // map null to "method" to filter it out later
            .collect(Collectors.groupingBy(ref -> ObjectUtils.notNull(PsiTreeUtil.getParentOfType(ref.getElement(), PsiMethod.class), method)));
        methodsToErrorCheck = ContainerUtil.filter(collect.keySet(), k->k != method);
      }
      else {
        methodsToErrorCheck = Collections.singletonList(methodCopyInClass);
      }

      for (PsiMethod psiMethodCopy : methodsToErrorCheck) {
        PsiCodeBlock body = psiMethodCopy.getBody();
        if (body != null && !errorChecks(body, superMethodsCalls)) return false;
      }

      return true;
    }
    // for anon/local have to copy containing method
    PsiMethod containingMethod = PsiTreeUtil.getParentOfType(containingClass, PsiMethod.class);
    if (containingMethod != null) {
      PsiMethod containingMethodCopy = DebugUtil
        .performPsiModification("Creating method copy", () -> createMethodCopy(project, containingMethod, -1, newParameterType));

      // find anon class in the copy
      int anonClassOffsetInContainingMethod = containingClass.getTextRange().getStartOffset() - containingMethod.getTextRange().getStartOffset();
      PsiElement element = containingMethodCopy.findElementAt(anonClassOffsetInContainingMethod);
      PsiClass containingClassCopy = PsiTreeUtil.getParentOfType(element, containingClass.getClass(), false);
      PsiMethod newMethodCopy = containingClassCopy.getMethods()[ArrayUtil.indexOf(containingClass.getMethods(), candidate.method)];
      PsiTypeElement paramTE = Objects.requireNonNull(newMethodCopy.getParameterList().getParameter(candidate.methodParameterIndex)).getTypeElement();
      ReplaceWithQuestionTFix.replaceType(project, paramTE, newParameterType);

      findSuperMethodCallsInside(newMethodCopy, candidate.superMethods, superMethodsCalls);
      return errorChecks(newMethodCopy.getBody(), superMethodsCalls);
    }

    return false;
  }

  private static void findSuperMethodCallsInside(@NotNull PsiMethod method, @NotNull List<PsiMethod> superMethods, @NotNull List<? super PsiElement> result) {
    PsiCodeBlock body = method.getBody();
    if (body == null || superMethods.isEmpty()) return;
    body.accept(new JavaRecursiveElementWalkingVisitor() {
      @Override
      public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
        PsiMethod called = expression.resolveMethod();
        if (superMethods.contains(called)) {
          result.add(expression);
        }
        super.visitMethodCallExpression(expression);
      }
    });
  }

  private static @NotNull PsiMethod createMethodCopy(@NotNull Project project,
                                                     PsiMethod method,
                                                     int methodParameterIndex, // -1 if no parameter to change
                                                     @NotNull PsiClassType newParameterExtends) {
    JavaDummyHolder dummyHolder = (JavaDummyHolder)DummyHolderFactory.createHolder(PsiManager.getInstance(project), method);
    PsiMethod methodCopy = (PsiMethod)dummyHolder.add(method);

    // force this dummy holder resolve recursive method calls to this "methodCopy" instead of original method
    dummyHolder.setInjectedDeclarations((processor, state, lastParent, place) ->
                                          processor.execute(methodCopy, state));

    if (methodParameterIndex != -1) {
      PsiTypeElement paramTE = Objects.requireNonNull(methodCopy.getParameterList().getParameter(methodParameterIndex)).getTypeElement();
      ReplaceWithQuestionTFix.replaceType(project, paramTE, newParameterExtends);
    }

    return methodCopy;
  }

  // copies class and returns copy of the method in this class
  private static @NotNull PsiMethod createClassCopy(@NotNull Project project,
                                          @Nullable PsiField field,
                                          @NotNull PsiClass containingClass, @NotNull PsiMethod method,
                                          @NotNull PsiMethod methodCopy,
                                          @NotNull PsiClassType newParameterExtends) {
    JavaDummyHolder dummyHolder = (JavaDummyHolder)DummyHolderFactory.createHolder(PsiManager.getInstance(project), containingClass);
    PsiClass classCopy = (PsiClass)dummyHolder.add(containingClass);

    if (field != null) {
      PsiField fieldCopy = classCopy.findFieldByName(field.getName(), false);
      ReplaceWithQuestionTFix.replaceType(project, fieldCopy.getTypeElement(), newParameterExtends);
    }

    int methodIndex = ArrayUtil.indexOf(containingClass.getMethods(), method);
    PsiMethod methodInClassCopy = classCopy.getMethods()[methodIndex];
    PsiMethod result = (PsiMethod)methodInClassCopy.replace(methodCopy); // patch method parameter type

    patchThisExpression(result, containingClass);

    return result;
  }

  private static void patchThisExpression(PsiMethod methodCopy, PsiClass containingClass) {
    PsiClass classCopy = methodCopy.getContainingClass();
    List<PsiThisExpression> these = new ArrayList<>();
    methodCopy.accept(new JavaRecursiveElementWalkingVisitor() {
      @Override
      public void visitThisExpression(@NotNull PsiThisExpression expression) {
        super.visitThisExpression(expression);
        if (PsiUtil.resolveClassInType(expression.getType()) == classCopy) {
          these.add(expression);
        }
      }
    });
    if (!these.isEmpty()) {
      PsiElementFactory f = PsiElementFactory.getInstance(containingClass.getProject());
      PsiParameter __this__ = f.createParameter("__this__", f.createType(containingClass));
      methodCopy.getParameterList().add(__this__);
      for (PsiThisExpression thisExpr : these) {
        PsiExpression newExpr = f.createExpressionFromText("__this__", thisExpr);
        thisExpr.replace(newExpr);
      }
    }
  }

  private static boolean errorChecks(@NotNull PsiElement method, @NotNull List<PsiElement> elementsToIgnore) {
    Ref<Boolean> hasError = Ref.create(false);
    JavaErrorCollector collector = new JavaErrorCollector(method.getContainingFile(), e -> hasError.set(true));
    method.accept(new PsiRecursiveElementWalkingVisitor() {
      @Override
      public void visitElement(@NotNull PsiElement element) {
        if (elementsToIgnore.contains(element)) return; // ignore sub-elements too
        collector.processElement(element);
        if (hasError.get()) {
          stopWalking();
        }
        super.visitElement(element);
      }
    });
    return !hasError.get();
  }

  private static PsiElement skipParensAndCastsUp(@NotNull PsiElement element) {
    PsiElement prev = element;
    PsiElement parent = element.getParent();
    while (parent instanceof PsiParenthesizedExpression ||
           parent instanceof PsiTypeCastExpression && ((PsiTypeCastExpression)parent).getOperand() == prev) {
      prev = parent;
      parent = parent.getParent();
    }
    return parent;
  }

  private static PsiExpression skipParensAndCastsDown(@Nullable PsiExpression element) {
    while (element instanceof PsiParenthesizedExpression || element instanceof PsiTypeCastExpression) {
      if (element instanceof PsiParenthesizedExpression) {
        element = ((PsiParenthesizedExpression)element).getExpression();
      }
      if (element instanceof PsiTypeCastExpression) {
        element = ((PsiTypeCastExpression)element).getOperand();
      }
    }
    return element;
  }

  // return field assigned to, type of the expression assigned from
  private static Pair<PsiField, PsiType> isAssignedToField(@NotNull PsiReference ref, PsiClass containingClass) {
    PsiElement refElement = ref.getElement();
    PsiElement parent = skipParensAndCastsUp(refElement);
    if (!(parent instanceof PsiAssignmentExpression) || ((PsiAssignmentExpression)parent).getOperationTokenType() != JavaTokenType.EQ) return null;
    PsiExpression r = ((PsiAssignmentExpression)parent).getRExpression();
    if (!PsiTreeUtil.isAncestor(r, refElement, false)) return null;
    PsiExpression l = skipParensAndCastsDown(((PsiAssignmentExpression)parent).getLExpression());
    if (!(l instanceof PsiReferenceExpression lExpression)) return null;
    PsiExpression lQualifier = skipParensAndCastsDown(lExpression.getQualifierExpression());
    if (lQualifier != null && !(lQualifier instanceof PsiThisExpression)) return null;
    PsiElement resolved = lExpression.resolve();
    if (!(resolved instanceof PsiField field)) return null;
    // too expensive to search for usages of public field otherwise
    if (!field.hasModifierProperty(PsiModifier.PRIVATE) &&
        !field.hasModifierProperty(PsiModifier.PACKAGE_LOCAL)) return null;
    PsiType type = r.getType();
    if (type == null) return null;
    if (field.getContainingClass() != containingClass) return null;
    return Pair.createNonNull(field, type);
  }

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(
      checkbox("REPORT_INVARIANT_CLASSES", InspectionGadgetsBundle.message("bounded.wildcard.report.invariant.option")),
      checkbox("REPORT_PRIVATE_METHODS", InspectionGadgetsBundle.message("bounded.wildcard.report.private.option")),
      checkbox("REPORT_INSTANCE_METHODS", InspectionGadgetsBundle.message("bounded.wildcard.report.instance.option")));
  }
}