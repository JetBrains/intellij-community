// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.move.moveInstanceMethod;

import com.intellij.codeInsight.ChangeContextUtil;
import com.intellij.codeInsight.daemon.impl.quickfix.CreateFromUsageUtils;
import com.intellij.codeInsight.generation.OverrideImplementUtil;
import com.intellij.ide.util.EditorHelper;
import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Ref;
import com.intellij.pom.java.JavaFeature;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.JavaRecursiveElementVisitor;
import com.intellij.psi.JavaRecursiveElementWalkingVisitor;
import com.intellij.psi.LambdaUtil;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiLambdaExpression;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiMethodReferenceExpression;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiNewExpression;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiThisExpression;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiVariable;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.javadoc.PsiDocTagValue;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.MethodSignature;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.move.MoveInstanceMembersUtil;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.refactoring.util.ConflictsUtil;
import com.intellij.refactoring.util.LambdaRefactoringUtil;
import com.intellij.refactoring.util.RefactoringChangeUtil;
import com.intellij.refactoring.util.RefactoringConflictsUtil;
import com.intellij.refactoring.util.RefactoringUIUtil;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewDescriptor;
import com.intellij.usageView.UsageViewUtil;
import com.intellij.util.CommonJavaRefactoringUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.VisibilityUtil;
import com.intellij.util.containers.MultiMap;
import com.siyeh.ig.psiutils.ExpressionUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.intellij.openapi.util.NlsContexts.DialogMessage;

public class MoveInstanceMethodProcessor extends BaseRefactoringProcessor{
  private static final Logger LOG = Logger.getInstance(MoveInstanceMethodProcessor.class);

  private PsiMethod myMethod;
  private PsiVariable myTargetVariable;
  private PsiClass myTargetClass;
  @PsiModifier.ModifierConstant private final String myNewVisibility;
  private final boolean myOpenInEditor;
  private final Map<PsiClass, String> myOldClassParameterNames;

  public MoveInstanceMethodProcessor(Project project,
                                     PsiMethod method,
                                     PsiVariable targetVariable,
                                     @PsiModifier.ModifierConstant String newVisibility,
                                     Map<PsiClass, String> oldClassParameterNames) {
    this(project, method, targetVariable, newVisibility, false, oldClassParameterNames);
  }

  public MoveInstanceMethodProcessor(Project project,
                                     PsiMethod method,
                                     PsiVariable targetVariable,
                                     @PsiModifier.ModifierConstant String newVisibility,
                                     boolean openInEditor,
                                     Map<PsiClass, String> oldClassParameterNames) {
    super(project);
    myMethod = method;
    myTargetVariable = targetVariable;
    myOpenInEditor = openInEditor;
    myOldClassParameterNames = oldClassParameterNames;
    LOG.assertTrue(myTargetVariable instanceof PsiParameter || myTargetVariable instanceof PsiField);
    LOG.assertTrue(myTargetVariable.getType() instanceof PsiClassType);
    final PsiType type = myTargetVariable.getType();
    LOG.assertTrue(type instanceof PsiClassType);
    myTargetClass = ((PsiClassType) type).resolve();
    myNewVisibility = newVisibility;
  }

  public PsiMethod getMethod() {
    return myMethod;
  }

  public PsiVariable getTargetVariable() {
    return myTargetVariable;
  }

  @Override
  protected @NotNull UsageViewDescriptor createUsageViewDescriptor(UsageInfo @NotNull [] usages) {
    return new MoveInstanceMethodViewDescriptor(myMethod, myTargetVariable, myTargetClass);
  }

  @Override
  protected boolean preprocessUsages(@NotNull Ref<UsageInfo[]> refUsages) {
    final UsageInfo[] usages = refUsages.get();
    MultiMap<PsiElement, @DialogMessage String> conflicts = new MultiMap<>();
    final Set<PsiMember> members = new HashSet<>();
    members.add(myMethod);
    if (myTargetVariable instanceof PsiField) members.add((PsiMember)myTargetVariable);
    if (!myTargetClass.isInterface()) {
      RefactoringConflictsUtil.getInstance()
        .analyzeAccessibilityConflictsAfterMemberMove(myTargetClass, myNewVisibility, members, conflicts);
    }
    else {
      for (UsageInfo usage : usages) {
        if (usage instanceof InheritorUsageInfo inheritorUsage) {
          RefactoringConflictsUtil.getInstance().analyzeAccessibilityConflictsAfterMemberMove(
            inheritorUsage.getInheritor(), myNewVisibility, members, conflicts);
        }
      }
    }

    if (myTargetVariable instanceof PsiParameter parameter) {
      final int index = myMethod.getParameterList().getParameterIndex(parameter);
      for (UsageInfo usageInfo : usages) {
        if (usageInfo instanceof MethodCallUsageInfo methodCallUsageInfo) {
          final PsiElement methodCall = methodCallUsageInfo.getMethodCallExpression();
          if (methodCall instanceof PsiMethodCallExpression call) {
            final PsiExpression[] expressions = call.getArgumentList().getExpressions();
            if (index < expressions.length) {
              PsiExpression instanceValue = expressions[index];
              instanceValue = RefactoringUtil.unparenthesizeExpression(instanceValue);
              if (instanceValue instanceof PsiLiteralExpression literal && literal.getValue() == null) {
                String message = JavaRefactoringBundle.message("0.contains.call.with.null.argument.for.parameter.1",
                                                           RefactoringUIUtil.getDescription(ConflictsUtil.getContainer(methodCall), true),
                                                           CommonRefactoringUtil.htmlEmphasize(parameter.getName()));
                conflicts.putValue(instanceValue, message);
              }
            }
          }
          else if (methodCall instanceof PsiMethodReferenceExpression methodRef && shouldBeExpandedToLambda(methodRef, index)) {
            conflicts.putValue(methodCall, JavaRefactoringBundle.message("expand.method.reference.warning"));
          }
        }
      }
    }

    try {
      ConflictsUtil.checkMethodConflicts(myTargetClass, myMethod, getPatternMethod(), conflicts);
    }
    catch (IncorrectOperationException ignored) {}

    return showConflicts(conflicts, usages);
  }

  /**
   * If collapse by second search is possible, then it's possible not to expand
   */
  private boolean shouldBeExpandedToLambda(PsiMethodReferenceExpression expression, int index) {
    PsiClassType.ClassResolveResult resolveResult = PsiUtil.resolveGenericsClassInType(expression.getFunctionalInterfaceType());
    PsiMethod interfaceMethod = LambdaUtil.getFunctionalInterfaceMethod(resolveResult);
    if (interfaceMethod != null) {
      MethodSignature methodSignature = interfaceMethod.getSignature(LambdaUtil.getSubstitutor(interfaceMethod, resolveResult));
      if (index == 0 && methodSignature.getParameterTypes().length > 0 &&
          methodSignature.getParameterTypes()[0].isAssignableFrom(myMethod.getParameterList().getParameters()[0].getType())) {
        return false;
      }
    }
    return true;
  }

  @Override
  protected UsageInfo @NotNull [] findUsages() {
    final PsiManager manager = myMethod.getManager();
    final GlobalSearchScope searchScope = GlobalSearchScope.allScope(manager.getProject());
    final List<UsageInfo> usages = new ArrayList<>();
    for (PsiReference ref : ReferencesSearch.search(myMethod, searchScope, false).asIterable()) {
      final PsiElement element = ref.getElement();
      if (element instanceof PsiReferenceExpression exp) {
        boolean isInternal = PsiTreeUtil.isAncestor(myMethod, element, true);
        usages.add(new MethodCallUsageInfo(exp, isInternal));
      }
      else if (element instanceof PsiDocTagValue value) {
        usages.add(new JavadocUsageInfo(value));
      }
      else {
        throw new UnknownReferenceTypeException(element.getLanguage());
      }
    }

    if (myTargetClass.isInterface() && !PsiUtil.isAvailable(JavaFeature.EXTENSION_METHODS, myTargetClass)) {
      addInheritorUsages(myTargetClass, searchScope, usages);
    }

    final PsiCodeBlock body = myMethod.getBody();
    if (body != null) {
      body.accept(new JavaRecursiveElementWalkingVisitor() {
        @Override public void visitNewExpression(@NotNull PsiNewExpression expression) {
          if (MoveInstanceMembersUtil.getClassReferencedByThis(expression) != null) {
            usages.add(new InternalUsageInfo(expression));
          }
          super.visitNewExpression(expression);
        }

        @Override public void visitReferenceExpression(@NotNull PsiReferenceExpression expression) {
          if (MoveInstanceMembersUtil.getClassReferencedByThis(expression) != null) {
            usages.add(new InternalUsageInfo(expression));
          }
          else if (!expression.isQualified() && myTargetVariable.equals(expression.resolve())) {
            usages.add(new InternalUsageInfo(expression));
          }

          super.visitReferenceExpression(expression);
        }
      });
    }

    return usages.toArray(UsageInfo.EMPTY_ARRAY);
  }

  private static void addInheritorUsages(PsiClass aClass, GlobalSearchScope searchScope, List<? super UsageInfo> usages) {
    for (PsiClass inheritor : ClassInheritorsSearch.search(aClass, searchScope, false).findAll()) {
      if (!inheritor.isInterface()) {
        usages.add(new InheritorUsageInfo(inheritor));
      }
      else {
        addInheritorUsages(inheritor, searchScope, usages);
      }
    }
  }

  @Override
  protected void refreshElements(PsiElement @NotNull [] elements) {
    LOG.assertTrue(elements.length == 3);
    myMethod = (PsiMethod) elements[0];
    myTargetVariable = (PsiVariable) elements[1];
    myTargetClass = (PsiClass) elements[2];
  }

  @Override
  protected @NotNull String getCommandName() {
    return RefactoringBundle.message("move.instance.method.command");
  }

  public PsiClass getTargetClass() {
    return myTargetClass;
  }

  @Override
  protected void performRefactoring(UsageInfo @NotNull [] usages) {
    PsiMethod patternMethod = createMethodToAdd();
    final List<PsiReference> docRefs = new ArrayList<>();
    for (UsageInfo usage : usages) {
      if (usage instanceof InheritorUsageInfo inheritorUsage) {
        addMethodToClass(inheritorUsage.getInheritor(), patternMethod, true);
      }
      else if (usage instanceof MethodCallUsageInfo methodCallUsage && !methodCallUsage.isInternal()) {
        final PsiElement expression = methodCallUsage.getMethodCallExpression();
        if (expression instanceof PsiMethodCallExpression call) {
          correctMethodCall(call, false);
        }
        else if (expression instanceof PsiMethodReferenceExpression methodReferenceExpression) {
          PsiExpression qualifierExpression = methodReferenceExpression.getQualifierExpression();

          if (myTargetVariable instanceof PsiParameter parameter && 
              shouldBeExpandedToLambda(methodReferenceExpression, myMethod.getParameterList().getParameterIndex(parameter))) {
            PsiLambdaExpression lambdaExpression = LambdaRefactoringUtil.convertMethodReferenceToLambda(methodReferenceExpression, false, true);
            if (lambdaExpression != null) {
              List<PsiExpression> returnExpressions = LambdaUtil.getReturnExpressions(lambdaExpression);
              if (!returnExpressions.isEmpty()) {
                correctMethodCall((PsiMethodCallExpression)returnExpressions.getFirst(), false);
              }
            }
          }
          else {
            String exprText;
            if (myTargetVariable instanceof PsiParameter ||
                qualifierExpression instanceof PsiReferenceExpression ref && ref.resolve() == myMethod.getContainingClass()) {
              exprText = myTargetVariable.getType().getCanonicalText();
            }
            else if (qualifierExpression instanceof PsiReferenceExpression) {
              exprText = qualifierExpression.getText() + "." + myTargetVariable.getName();
            }
            else {
              exprText = myTargetVariable.getName();
            }
            PsiExpression newQualifier = JavaPsiFacade.getElementFactory(myProject).createExpressionFromText(exprText, null);
            methodReferenceExpression.setQualifierExpression(newQualifier);
            JavaCodeStyleManager.getInstance(myProject).shortenClassReferences(expression);
          }
        }
      }
      else if (usage instanceof JavadocUsageInfo) {
        docRefs.add(usage.getElement().getReference());
      }
    }

    try {
      final PsiModifierList modifierList = patternMethod.getModifierList();
      if (myTargetClass.isInterface() && !myMethod.hasModifierProperty(PsiModifier.ABSTRACT)) {
        if (!PsiUtil.isAvailable(JavaFeature.EXTENSION_METHODS, myTargetClass)) {
          patternMethod.getBody().delete();
          modifierList.setModifierProperty(PsiModifier.DEFAULT, false);
        }
        else {
          modifierList.setModifierProperty(PsiModifier.DEFAULT, true);
        }
        RefactoringUtil.makeMethodAbstract(myTargetClass, patternMethod);
      }
      else if (myMethod.hasModifierProperty(PsiModifier.DEFAULT)) {
        modifierList.setModifierProperty(PsiModifier.DEFAULT, false);
        VisibilityUtil.setVisibility(modifierList, PsiModifier.PUBLIC);
      }

      final PsiMethod method = addMethodToClass(myTargetClass, patternMethod, false);
      myMethod.delete();
      for (PsiReference reference : docRefs) {
        reference.bindToElement(method);
      }
      VisibilityUtil.fixVisibility(UsageViewUtil.toElements(usages), method, myNewVisibility);

      if (myOpenInEditor) {
        EditorHelper.openInEditor(method);
      }
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  private void correctMethodCall(PsiMethodCallExpression expression, boolean isInternalCall) {
    try {
      PsiReferenceExpression methodExpression = expression.getMethodExpression();
      if (!methodExpression.isReferenceTo(myMethod)) return;
      final PsiExpression oldQualifier = methodExpression.getQualifierExpression();
      PsiExpression newQualifier = null;
      final PsiClass classReferencedByThis = MoveInstanceMembersUtil.getClassReferencedByThis(methodExpression);
      if (myTargetVariable instanceof PsiParameter param) {
        final int index = myMethod.getParameterList().getParameterIndex(param);
        final PsiExpression[] arguments = expression.getArgumentList().getExpressions();
        if (index < arguments.length) {
          newQualifier = (PsiExpression)arguments[index].copy();
          arguments[index].delete();
        }
      }
      else {
        VisibilityUtil.escalateVisibility((PsiField)myTargetVariable, expression);
        String newQualifierName = myTargetVariable.getName();
        if (oldQualifier != null && myTargetVariable instanceof PsiField field) {
          final PsiClass aClass = PsiUtil.resolveClassInClassTypeOnly(oldQualifier.getType());
          if (aClass == field.getContainingClass()) {
            newQualifierName = oldQualifier.getText() + "." + newQualifierName;
          }
        }
        newQualifier = JavaPsiFacade.getElementFactory(myMethod.getProject()).createExpressionFromText(newQualifierName, null);
      }

      PsiExpression newArgument = null;
      if (classReferencedByThis != null && !myOldClassParameterNames.isEmpty()) {
        PsiClass containingClass = PsiTreeUtil.getParentOfType(methodExpression, PsiClass.class);
        @NonNls String thisArgumentText;
        if (myMethod.getManager().areElementsEquivalent(containingClass, classReferencedByThis)
            && myOldClassParameterNames.containsKey(containingClass)) {
          thisArgumentText = "this";
        }
        else {
          final String name = classReferencedByThis.getName();
          thisArgumentText = name != null ? name + ".this" : "this";
        }

        newArgument = JavaPsiFacade.getElementFactory(myMethod.getProject()).createExpressionFromText(thisArgumentText, null);
      } else {
        if (!isInternalCall
            && oldQualifier != null
            && oldQualifier.getType() instanceof PsiClassType classType
            && getParameterNameToCreate(classType.resolve()) != null) {
          //replace is needed in case old qualifier is e.g. the same as field as target variable
          newArgument = replaceRefsToTargetVariable(oldQualifier);
        }
      }

      if (newArgument != null) {
        expression.getArgumentList().add(newArgument);
      }

      if (newQualifier != null) {
        if (newQualifier instanceof PsiThisExpression thisExp && thisExp.getQualifier() == null) {
          //Remove now redundant 'this' qualifier
          if (oldQualifier != null) oldQualifier.delete();
        }
        else {
          final PsiReferenceExpression refExpr = (PsiReferenceExpression)JavaPsiFacade.getElementFactory(myMethod.getProject())
              .createExpressionFromText("q." + myMethod.getName(), null);
          refExpr.getQualifierExpression().replace(newQualifier);
          methodExpression.replace(refExpr);
        }
      }
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  private PsiExpression replaceRefsToTargetVariable(PsiExpression expression) {
    final PsiManager manager = expression.getManager();
    if (ExpressionUtils.isReferenceTo(expression, myTargetVariable)) {
      return createThisExpr(manager);
    }

    expression.accept(new JavaRecursiveElementVisitor() {
      @Override public void visitReferenceExpression(@NotNull PsiReferenceExpression expression) {
        super.visitReferenceExpression(expression);
        if (expression.isReferenceTo(myTargetVariable)) {
          try {
            expression.replace(createThisExpr(manager));
          }
          catch (IncorrectOperationException e) {
            LOG.error(e);
          }
        }
      }
    });

    return expression;
  }

  private static PsiExpression createThisExpr(PsiManager manager)  {
    try {
      return JavaPsiFacade.getElementFactory(manager.getProject()).createExpressionFromText("this", null);
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
      return null;
    }
  }

  private static PsiMethod addMethodToClass(PsiClass aClass, PsiMethod patternMethod, boolean canAddOverride) {
    try {
      final PsiMethod method = (PsiMethod)aClass.add(patternMethod);
      ChangeContextUtil.decodeContextInfo(method, null, null);
      if (canAddOverride && OverrideImplementUtil.isInsertOverride(method, aClass)) {
        method.getModifierList().addAnnotation(CommonClassNames.JAVA_LANG_OVERRIDE);
      }
      return method;
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
      return null;
    }
  }

  private PsiMethod createMethodToAdd() {
    ChangeContextUtil.encodeContextInfo(myMethod, true);
    try {
      //correct internal references
      final PsiCodeBlock body = myMethod.getBody();
      if (body != null) {
        replaceReferences(body);
      }
      else {
        if (myMethod.hasModifierProperty(PsiModifier.ABSTRACT) &&
            !myTargetClass.isInterface() && !myTargetClass.hasModifierProperty(PsiModifier.ABSTRACT)) {
          CreateFromUsageUtils.setupMethodBody(myMethod);
          PsiUtil.setModifierProperty(myMethod, PsiModifier.ABSTRACT, false);
        }
      }

      final PsiMethod methodCopy = getPatternMethod();

      final List<PsiParameter> newParameters = Arrays.asList(methodCopy.getParameterList().getParameters());
      CommonJavaRefactoringUtil.fixJavadocsForParams(methodCopy, new HashSet<>(newParameters));
      return methodCopy;
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
      return myMethod;
    }
  }

  private void replaceReferences(PsiCodeBlock body) {
    final PsiManager manager = myMethod.getManager();
    JavaPsiFacade facade = JavaPsiFacade.getInstance(manager.getProject());
    final PsiElementFactory factory = facade.getElementFactory();
    final Map<PsiElement, PsiElement> replaceMap = new HashMap<>();
    body.accept(new JavaRecursiveElementVisitor() {
      @Override public void visitThisExpression(@NotNull PsiThisExpression expression) {
        final PsiClass classReferencedByThis = MoveInstanceMembersUtil.getClassReferencedByThis(expression);
        if (classReferencedByThis != null && !PsiTreeUtil.isAncestor(myMethod, classReferencedByThis, false)) {
          final PsiElementFactory factory = JavaPsiFacade.getElementFactory(myProject);
          String paramName = getParameterNameToCreate(classReferencedByThis);
          try {
            final PsiExpression refExpression = factory.createExpressionFromText(paramName, null);
            replaceMap.put(expression, refExpression);
          }
          catch (IncorrectOperationException e) {
            LOG.error(e);
          }
        }
      }

      @Override public void visitReferenceExpression(@NotNull PsiReferenceExpression expression) {
        try {
          final PsiExpression qualifier = expression.getQualifierExpression();
          final PsiElement resolved = expression.resolve();
          if (ExpressionUtils.isReferenceTo(qualifier, myTargetVariable)) {
            if (resolved instanceof PsiField field) {
              String fieldName = field.getName();
              for (PsiParameter parameter : myMethod.getParameterList().getParameters()) {
                if (Comparing.strEqual(parameter.getName(), fieldName) ||
                    facade.getResolveHelper().resolveReferencedVariable(fieldName, expression) != null) {
                  qualifier.replace(factory.createExpressionFromText("this", null));
                  return;
                }
              }
            }
            if (expression instanceof PsiMethodReferenceExpression) {
              qualifier.replace(factory.createExpressionFromText("this", null));
            }
            else {
              //Target is a field, replace target.m -> m
              qualifier.delete();
            }
            return;
          }
          if (myTargetVariable.equals(resolved)) {
            PsiThisExpression thisExpression = RefactoringChangeUtil.createThisExpression(
              manager, PsiTreeUtil.isAncestor(myMethod, PsiUtil.getContainingClass(expression), true) ? myTargetClass : null);
            replaceMap.put(expression, thisExpression);
            return;
          }
          if (!myMethod.equals(resolved)) {
            PsiClass classReferencedByThis = MoveInstanceMembersUtil.getClassReferencedByThis(expression);
            if (classReferencedByThis != null) {
              final String paramName = getParameterNameToCreate(classReferencedByThis);
              if (paramName != null) {
                PsiReferenceExpression newQualifier = (PsiReferenceExpression)factory.createExpressionFromText(paramName, null);
                expression.setQualifierExpression(newQualifier);
                return;
              }
            }
          }
          super.visitReferenceExpression(expression);
        }
        catch (IncorrectOperationException e) {
          LOG.error(e);
        }
      }

      @Override public void visitNewExpression(@NotNull PsiNewExpression expression) {
        try {
          final PsiExpression qualifier = expression.getQualifier();
          if (ExpressionUtils.isReferenceTo(qualifier, myTargetVariable)) {
            //Target is a field, replace target.new A() -> new A()
            qualifier.delete();
          }
          else {
            final PsiClass classReferencedByThis = MoveInstanceMembersUtil.getClassReferencedByThis(expression);
            if (classReferencedByThis != null) {
              if (qualifier != null) qualifier.delete();
              final String paramName = getParameterNameToCreate(classReferencedByThis);
              final PsiExpression newExpression = factory.createExpressionFromText(paramName + "." + expression.getText(), null);
              replaceMap.put(expression, newExpression);
            }
          }
          super.visitNewExpression(expression);
        }
        catch (IncorrectOperationException e) {
          LOG.error(e);
        }
      }

      @Override public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
        correctMethodCall(expression, true);
        super.visitMethodCallExpression(expression);
      }
    });
    for (Map.Entry<PsiElement, PsiElement> entry : replaceMap.entrySet()) {
      entry.getKey().replace(entry.getValue());
    }
  }

  private PsiMethod getPatternMethod() {
    final PsiMethod methodCopy = (PsiMethod)myMethod.copy();
    String name = myTargetClass.isInterface()
                  ? PsiModifier.PUBLIC
                  : !Comparing.strEqual(myNewVisibility, VisibilityUtil.ESCALATE_VISIBILITY) ? myNewVisibility : null;
    if (name != null) {
      PsiUtil.setModifierProperty(methodCopy, name, true);
    }
    if (myTargetVariable instanceof PsiParameter param) {
      final int index = myMethod.getParameterList().getParameterIndex(param);
      methodCopy.getParameterList().getParameters()[index].delete();
    }

    addParameters(JavaPsiFacade.getElementFactory(myProject), methodCopy, myTargetClass.isInterface());
    return methodCopy;
  }

  private void addParameters(PsiElementFactory factory, PsiMethod methodCopy, boolean isInterface) {
    final Set<Map.Entry<PsiClass, String>> entries = myOldClassParameterNames.entrySet();
    for (Map.Entry<PsiClass, String> entry : entries) {
      final PsiClassType type = factory.createType(entry.getKey());
      final PsiParameter parameter = factory.createParameter(entry.getValue(), type);
      if (isInterface) {
        PsiUtil.setModifierProperty(parameter, PsiModifier.FINAL, false);
      }
      methodCopy.getParameterList().add(parameter);
    }
  }

  private String getParameterNameToCreate(PsiClass aClass) {
    return myOldClassParameterNames.get(aClass);
  }
}
