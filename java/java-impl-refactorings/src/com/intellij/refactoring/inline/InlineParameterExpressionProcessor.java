// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.inline;

import com.intellij.codeInsight.ExceptionUtil;
import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.*;
import com.intellij.psi.controlFlow.DefUseUtil;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.changeSignature.ChangeSignatureProcessorBase;
import com.intellij.refactoring.changeSignature.JavaChangeInfo;
import com.intellij.refactoring.changeSignature.JavaChangeInfoImpl;
import com.intellij.refactoring.changeSignature.ParameterInfoImpl;
import com.intellij.refactoring.safeDelete.JavaSafeDeleteProcessor;
import com.intellij.refactoring.util.CanonicalTypes;
import com.intellij.refactoring.util.InlineUtil;
import com.intellij.refactoring.util.RefactoringUIUtil;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewDescriptor;
import com.intellij.usageView.UsageViewUtil;
import com.intellij.util.CommonJavaRefactoringUtil;
import com.intellij.util.JavaPsiConstructorUtil;
import com.intellij.util.VisibilityUtil;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;


public class InlineParameterExpressionProcessor extends BaseRefactoringProcessor {
  private static final Logger LOG = Logger.getInstance(InlineParameterExpressionProcessor.class);
  public static final Key<Boolean> CREATE_LOCAL_FOR_TESTS = Key.create("CREATE_INLINE_PARAMETER_LOCAL_FOR_TESTS");

  private final PsiCallExpression myMethodCall;
  @NotNull
  private final PsiMethod myMethod;
  private final PsiParameter myParameter;
  private PsiExpression myInitializer;
  private final boolean mySameClass;
  private final PsiCodeBlock myCallingBlock;
  private final boolean myCreateLocal;

  private JavaChangeInfo myChangeInfo;
  private UsageInfo[] myChangeSignatureUsages;

  public InlineParameterExpressionProcessor(PsiCallExpression methodCall,
                                            @NotNull PsiMethod method,
                                            PsiParameter parameter,
                                            PsiExpression initializer,
                                            boolean createLocal) {
    super(method.getProject());
    myMethodCall = methodCall;
    myMethod = method;
    myParameter = parameter;
    myInitializer = initializer;
    myCreateLocal = createLocal;

    PsiClass callingClass = PsiTreeUtil.getParentOfType(methodCall, PsiClass.class);
    mySameClass = (callingClass == myMethod.getContainingClass());
    myCallingBlock = PsiTreeUtil.getTopmostParentOfType(myMethodCall, PsiCodeBlock.class);
  }

  @NotNull
  @Override
  protected String getCommandName() {
    return InlineParameterHandler.getRefactoringName();
  }

  @NotNull
  @Override
  protected UsageViewDescriptor createUsageViewDescriptor(UsageInfo @NotNull [] usages) {
    return new InlineViewDescriptor(myParameter);
  }

  @Override
  protected UsageInfo @NotNull [] findUsages() {
    int parameterIndex = myMethod.getParameterList().getParameterIndex(myParameter);
    final Map<PsiVariable, PsiElement> localToParamRef = new HashMap<>();
    final PsiExpression[] arguments = myMethodCall.getArgumentList().getExpressions();
    for (int i = 0; i < arguments.length; i++) {
      if (i != parameterIndex && arguments[i] instanceof PsiReferenceExpression ref) {
        final PsiElement element = ref.resolve();
        if (PsiUtil.isJvmLocalVariable(element)) {
          final PsiParameter param = myMethod.getParameterList().getParameters()[i];
          final PsiExpression paramRef =
            JavaPsiFacade.getElementFactory(myMethod.getProject()).createExpressionFromText(param.getName(), myMethod);
          localToParamRef.put((PsiVariable)element, paramRef);
        }
      }
    }

    final List<UsageInfo> result = new ArrayList<>();
    myInitializer.accept(new JavaRecursiveElementVisitor() {
      @Override
      public void visitReferenceExpression(final @NotNull PsiReferenceExpression expression) {
        super.visitReferenceExpression(expression);
        final PsiElement element = expression.resolve();
        if (element instanceof PsiLocalVariable localVariable) {
          final PsiElement[] elements = DefUseUtil.getDefs(myCallingBlock, localVariable, expression);
          if (elements.length == 1) {
            PsiExpression localInitializer = null;
            if (elements[0] instanceof PsiLocalVariable var) {
              localInitializer = var.getInitializer();
            }
            else if (elements[0] instanceof PsiAssignmentExpression assignment) {
              localInitializer = assignment.getRExpression();
            }
            else if (elements[0] instanceof PsiReferenceExpression ref) {
              final PsiElement parent = ref.getParent();
              if (parent instanceof PsiAssignmentExpression assignment && assignment.getLExpression() == ref) {
                localInitializer = assignment.getRExpression();
              }
            }
            if (localInitializer != null) {
              final PsiElement replacement;
              if (localToParamRef.containsKey(localVariable)) {
                replacement = localToParamRef.get(localVariable);
              }
              else {
                replacement = replaceArgs(localToParamRef, localInitializer.copy());
              }
              result.add(new LocalReplacementUsageInfo(expression, replacement));
            }
          }
        }
      }
    });

    if (!myCreateLocal) {
      for (PsiReference ref : ReferencesSearch.search(myParameter).findAll()) {
        result.add(new UsageInfo(ref));
      }
    }

    final PsiParameter[] parameters = myMethod.getParameterList().getParameters();
    final List<ParameterInfoImpl> psiParameters = new ArrayList<>();
    int paramIdx = 0;
    final String paramName = myParameter.getName();
    for (PsiParameter param : parameters) {
      if (!Comparing.strEqual(paramName, param.getName())) {
        psiParameters.add(ParameterInfoImpl.create(paramIdx).withName(param.getName()).withType(param.getType()));
      }
      paramIdx++;
    }

    PsiType returnType = myMethod.getReturnType();
    myChangeInfo = new JavaChangeInfoImpl(VisibilityUtil.getVisibilityModifier(myMethod.getModifierList()), myMethod, myMethod.getName(),
                                          returnType != null ? CanonicalTypes.createTypeWrapper(returnType) : null,
                                          psiParameters.toArray(new ParameterInfoImpl[0]),
                                          null,
                                          false,
                                          Collections.emptySet(),
                                          Collections.emptySet() );
    myChangeSignatureUsages = ChangeSignatureProcessorBase.findUsages(myChangeInfo);

    final UsageInfo[] usageInfos = result.toArray(UsageInfo.EMPTY_ARRAY);
    return UsageViewUtil.removeDuplicatedUsages(usageInfos);
  }

  private static PsiElement replaceArgs(Map<PsiVariable, PsiElement> elementsToReplace, PsiElement expression) {
    final Map<PsiElement, PsiElement> replacements = new HashMap<>();
    expression.accept(new JavaRecursiveElementVisitor() {
      @Override
      public void visitReferenceExpression(@NotNull PsiReferenceExpression referenceExpression) {
        super.visitReferenceExpression(referenceExpression);
        final PsiElement resolved = referenceExpression.resolve();
        if (resolved instanceof PsiVariable variable) {
          final PsiElement replacement = elementsToReplace.get(variable);
          if (replacement != null) {
            replacements.put(referenceExpression, replacement);
          }
        }
      }
    });
    return CommonJavaRefactoringUtil.replaceElementsWithMap(expression, replacements);
  }

  @Override
  protected boolean preprocessUsages(@NotNull Ref<UsageInfo[]> refUsages) {
    final MultiMap<PsiElement, String> conflicts = new MultiMap<>();
    JavaSafeDeleteProcessor.collectMethodConflicts(conflicts, myMethod, myParameter);
    final UsageInfo[] usages = refUsages.get();
    final InaccessibleExpressionsDetector detector = new InaccessibleExpressionsDetector(conflicts);
    myInitializer.accept(detector);
    for (UsageInfo usage : usages) {
      if (usage instanceof LocalReplacementUsageInfo usageInfo) {
        final PsiElement replacement = usageInfo.getReplacement();
        if (replacement != null) {
          replacement.accept(detector);
        }
      }
    }

    final Set<PsiVariable> vars = new HashSet<>();
    for (UsageInfo usage : usages) {
      if (usage instanceof LocalReplacementUsageInfo usageInfo) {
        final PsiVariable var = usageInfo.getVariable();
        if (var != null) {
          vars.add(var);
        }
      }
    }
    for (PsiVariable var : vars) {
      for (PsiReference ref : ReferencesSearch.search(var)) {
        final PsiElement element = ref.getElement();
        if (element instanceof PsiExpression exp && isAccessedForWriting(exp)) {
          conflicts.putValue(element, JavaRefactoringBundle.message("inline.parameter.initializer.depends.on.inaccessible.value"));
          break;
        }
      }
    }
    return showConflicts(conflicts, usages);
  }

  private static boolean isAccessedForWriting (PsiExpression expr) {
    PsiElement parent = expr.getParent();
    while (parent instanceof PsiArrayAccessExpression || parent instanceof PsiParenthesizedExpression) {
      expr = (PsiExpression)parent;
      parent = parent.getParent();
    }
    return PsiUtil.isAccessedForWriting(expr);
  }

  @Override
  protected void performRefactoring(UsageInfo @NotNull [] usages) {
    final List<PsiClassType> thrownExceptions = ExceptionUtil.getThrownCheckedExceptions(myInitializer);
    final Set<PsiVariable> varsUsedInInitializer = new HashSet<>();
    final Set<PsiJavaCodeReferenceElement> paramRefsToInline = new HashSet<>();
    final Map<PsiElement, PsiElement> replacements = new HashMap<>();
    for (UsageInfo usage : usages) {
      if (usage instanceof LocalReplacementUsageInfo usageInfo) {
        final PsiElement element = usageInfo.getElement();
        final PsiElement replacement = usageInfo.getReplacement();
        if (element != null && replacement != null) {
          replacements.put(element, replacement);
        }
        varsUsedInInitializer.add(usageInfo.getVariable());
      }
      else {
        LOG.assertTrue(!myCreateLocal);
        paramRefsToInline.add((PsiJavaCodeReferenceElement)usage.getElement());
      }
    }
    myInitializer = (PsiExpression)CommonJavaRefactoringUtil.replaceElementsWithMap(myInitializer, replacements);

    if (myCreateLocal) {
      final PsiCodeBlock body = myMethod.getBody();
      if (body != null) {
        PsiElement anchor = findAnchorForLocalVariableDeclaration(body);
        final PsiElementFactory factory = JavaPsiFacade.getElementFactory(myMethod.getProject());
        PsiExpression refExpression = factory.createExpressionFromText(myParameter.getName(), anchor);
        PsiDeclarationStatement localDeclaration =
          factory.createVariableDeclarationStatement(myParameter.getName(), myParameter.getType(), refExpression);

        localDeclaration = (PsiDeclarationStatement)body.addAfter(localDeclaration, anchor);
        final PsiLocalVariable declaredVar = (PsiLocalVariable)localDeclaration.getDeclaredElements()[0];
        PsiUtil.setModifierProperty(declaredVar, PsiModifier.FINAL, myParameter.hasModifierProperty(PsiModifier.FINAL));
        InlineUtil.inlineVariable(myParameter, myInitializer, (PsiReferenceExpression)declaredVar.getInitializer());
      }
    }
    else {
      for (PsiJavaCodeReferenceElement paramRef : paramRefsToInline) {
        InlineUtil.inlineVariable(myParameter, myInitializer, paramRef);
      }
    }

    //delete var if it becomes unused
    for (PsiVariable variable : varsUsedInInitializer) {
      if (variable != null && variable.isValid()) {
        if (ReferencesSearch.search(variable).findFirst() == null) {
          variable.delete();
        }
      }
    }

    ChangeSignatureProcessorBase.doChangeSignature(myChangeInfo, myChangeSignatureUsages);

    if (!thrownExceptions.isEmpty()) {
      for (PsiClassType exception : thrownExceptions) {
        PsiClass exceptionClass = exception.resolve();
        if (exceptionClass != null) {
          PsiUtil.addException(myMethod, exceptionClass);
        }
      }
    }
  }

  @Nullable
  private PsiElement findAnchorForLocalVariableDeclaration(PsiCodeBlock body) {
    PsiMethodCallExpression call = JavaPsiConstructorUtil.findThisOrSuperCallInConstructor(myMethod);
    if (call != null) {
      return call.getParent();
    }
    return body.getLBrace();
  }

  private static class LocalReplacementUsageInfo extends UsageInfo {
    private final PsiElement myReplacement;
    private final PsiVariable myVariable;

    LocalReplacementUsageInfo(@NotNull PsiReference element, @NotNull PsiElement replacement) {
      super(element);
      final PsiElement resolved = element.resolve();
      myVariable = resolved instanceof PsiVariable var ? var : null;
      myReplacement = replacement;
    }

    @Nullable
    public PsiElement getReplacement() {
      return myReplacement.isValid() ? myReplacement : null;
    }

    @Nullable
    public PsiVariable getVariable() {
      return myVariable != null && myVariable.isValid() ? myVariable : null;
    }
  }

  private class InaccessibleExpressionsDetector extends JavaRecursiveElementWalkingVisitor {
    private final MultiMap<PsiElement, String> myConflicts;

    InaccessibleExpressionsDetector(MultiMap<PsiElement, String> conflicts) {
      myConflicts = conflicts;
    }

    @Override
    public void visitReferenceExpression(@NotNull PsiReferenceExpression expression) {
      super.visitReferenceExpression(expression);
      final PsiElement element = expression.resolve();
      if (element instanceof PsiMember member && !member.hasModifierProperty(PsiModifier.STATIC) && !(member instanceof PsiClass)) {
        if (myMethod.hasModifierProperty(PsiModifier.STATIC)) {
          myConflicts.putValue(expression, JavaRefactoringBundle.message("inline.parameter.dependency.unavailable.in.parameter.method",
                                                                         RefactoringUIUtil.getDescription(element, false)));
        }
      }
      else if (element instanceof PsiMethod || element instanceof PsiField) {
        if (!mySameClass && !((PsiModifierListOwner)element).hasModifierProperty(PsiModifier.STATIC)) {
          myConflicts.putValue(expression, JavaRefactoringBundle.message("inline.parameter.depends.on.non.static",
                                                                         RefactoringUIUtil.getDescription(element, true)));
        }
        else if (!PsiUtil.isAccessible((PsiMember)element, myMethod, null)) {
          myConflicts.putValue(expression, JavaRefactoringBundle.message("inline.parameter.dependency.unavailable.in.parameter.method",
                                                                         RefactoringUIUtil.getDescription(element, true)));
        }
      }
      else if (element instanceof PsiParameter param && PsiTreeUtil.isAncestor(param.getDeclarationScope(), myInitializer, true)) {
        boolean bound = false;
        for (PsiParameter parameter : myMethod.getParameterList().getParameters()) {
          if (parameter == myParameter) continue;
          if (parameter.getType().equals(param.getType()) && parameter.getName().equals(param.getName())) {
            bound = true;
            break;
          }
        }
        if (!bound) {
          myConflicts.putValue(expression, JavaRefactoringBundle.message("inline.parameter.depends.on.caller.parameter",
                                                                         RefactoringUIUtil.getDescription(element, false)));
        }
      }
    }

    @Override
    public void visitThisExpression(@NotNull PsiThisExpression thisExpression) {
      super.visitThisExpression(thisExpression);
      final PsiJavaCodeReferenceElement qualifier = thisExpression.getQualifier();
      PsiElement containingClass;
      if (qualifier != null) {
        containingClass = qualifier.resolve();
      }
      else {
        containingClass = PsiTreeUtil.getParentOfType(myMethodCall, PsiClass.class);
      }
      final PsiClass methodContainingClass = myMethod.getContainingClass();
      LOG.assertTrue(methodContainingClass != null);
      if (!PsiTreeUtil.isAncestor(containingClass, methodContainingClass, false) || myMethod.hasModifierProperty(PsiModifier.STATIC)) {
        myConflicts.putValue(thisExpression, JavaRefactoringBundle.message("inline.parameter.dependency.unavailable.in.parameter.method",
                                                                           "<b><code>this<code></b>"));
      }
    }

    @Override
    public void visitNewExpression(@NotNull PsiNewExpression expression) {
      super.visitNewExpression(expression);
      final PsiJavaCodeReferenceElement reference = expression.getClassOrAnonymousClassReference();
      if (reference != null && reference.resolve() instanceof PsiClass aClass) {
        if (!expression.isArrayCreation() && myMethod.hasModifierProperty(PsiModifier.STATIC) &&
            (PsiUtil.isInnerClass(aClass) || PsiUtil.isLocalClass(aClass))) {
          myConflicts.putValue(reference, JavaRefactoringBundle.message("inline.parameter.depends.on.non.static.class",
                                                                        RefactoringUIUtil.getDescription(aClass, true)));
        }
        else if (!PsiUtil.isAccessible(aClass, myMethod, null)) {
          myConflicts.putValue(expression, JavaRefactoringBundle.message("inline.parameter.dependency.unavailable.in.parameter.method",
                                                                         RefactoringUIUtil.getDescription(aClass, true)));
        }
        else {
          final PsiClass methodContainingClass = myMethod.getContainingClass();
          LOG.assertTrue(methodContainingClass != null);
          if (!PsiTreeUtil.isAncestor(myMethod, aClass, false)) {
            PsiElement parent = aClass;
            while ((parent = parent.getParent()) instanceof PsiClass parentClass) {
              if (!PsiUtil.isAccessible(parentClass, myMethod, null)) {
                break;
              }
            }
            if (!(parent instanceof PsiFile)) {
              myConflicts.putValue(expression, JavaRefactoringBundle.message("inline.parameter.dependency.unavailable.in.parameter.method",
                                                                             RefactoringUIUtil.getDescription(aClass, true)));
            }
          }
        }
      }
    }
  }
}
