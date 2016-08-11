/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.refactoring.move.moveInstanceMethod;

import com.intellij.codeInsight.ChangeContextUtil;
import com.intellij.codeInsight.generation.OverrideImplementUtil;
import com.intellij.ide.util.EditorHelper;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.javadoc.PsiDocTagValue;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.move.MoveInstanceMembersUtil;
import com.intellij.refactoring.util.*;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewDescriptor;
import com.intellij.usageView.UsageViewUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.VisibilityUtil;
import com.intellij.util.containers.HashSet;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * @author ven
 */
public class MoveInstanceMethodProcessor extends BaseRefactoringProcessor{
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.move.moveInstanceMethod.MoveInstanceMethodProcessor");

  public PsiMethod getMethod() {
    return myMethod;
  }

  public PsiVariable getTargetVariable() {
    return myTargetVariable;
  }

  private PsiMethod myMethod;
  private PsiVariable myTargetVariable;
  private PsiClass myTargetClass;
  private final String myNewVisibility;
  private final boolean myOpenInEditor;
  private final Map<PsiClass, String> myOldClassParameterNames;

  public MoveInstanceMethodProcessor(final Project project,
                                   final PsiMethod method,
                                   final PsiVariable targetVariable,
                                   final String newVisibility,
                                   final Map<PsiClass, String> oldClassParameterNames) {
    this(project, method, targetVariable, newVisibility, false, oldClassParameterNames);
  }

  public MoveInstanceMethodProcessor(final Project project,
                                     final PsiMethod method,
                                     final PsiVariable targetVariable,
                                     final String newVisibility,
                                     boolean openInEditor, 
                                     final Map<PsiClass, String> oldClassParameterNames) {
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

  @NotNull
  protected UsageViewDescriptor createUsageViewDescriptor(@NotNull UsageInfo[] usages) {
    return new MoveInstanceMethodViewDescriptor(myMethod, myTargetVariable, myTargetClass);
  }

  protected boolean preprocessUsages(@NotNull Ref<UsageInfo[]> refUsages) {
    final UsageInfo[] usages = refUsages.get();
    MultiMap<PsiElement, String> conflicts = new MultiMap<>();
    final Set<PsiMember> members = new HashSet<>();
    members.add(myMethod);
    if (myTargetVariable instanceof PsiField) members.add((PsiMember)myTargetVariable);
    if (!myTargetClass.isInterface()) {
      RefactoringConflictsUtil.analyzeAccessibilityConflicts(members, myTargetClass, conflicts, myNewVisibility);
    }
    else {
      for (final UsageInfo usage : usages) {
        if (usage instanceof InheritorUsageInfo) {
          RefactoringConflictsUtil.analyzeAccessibilityConflicts(
            members, ((InheritorUsageInfo)usage).getInheritor(), conflicts, myNewVisibility);
        }
      }
    }

    if (myTargetVariable instanceof PsiParameter) {
      PsiParameter parameter = (PsiParameter)myTargetVariable;
      for (final UsageInfo usageInfo : usages) {
        if (usageInfo instanceof MethodCallUsageInfo) {
          final PsiElement methodCall = ((MethodCallUsageInfo)usageInfo).getMethodCallExpression();
          if (methodCall instanceof PsiMethodCallExpression) {
            final PsiExpression[] expressions = ((PsiMethodCallExpression)methodCall).getArgumentList().getExpressions();
            final int index = myMethod.getParameterList().getParameterIndex(parameter);
            if (index < expressions.length) {
              PsiExpression instanceValue = expressions[index];
              instanceValue = RefactoringUtil.unparenthesizeExpression(instanceValue);
              if (instanceValue instanceof PsiLiteralExpression && ((PsiLiteralExpression)instanceValue).getValue() == null) {
                String message = RefactoringBundle.message("0.contains.call.with.null.argument.for.parameter.1",
                                                           RefactoringUIUtil.getDescription(ConflictsUtil.getContainer(methodCall), true),
                                                           CommonRefactoringUtil.htmlEmphasize(parameter.getName()));
                conflicts.putValue(instanceValue, message);
              }
            }
          } else if (methodCall instanceof PsiMethodReferenceExpression) {
            conflicts.putValue(methodCall, "Method reference would be broken after move");
          }
        }
      }
    }

    try {
      ConflictsUtil.checkMethodConflicts(myTargetClass, myMethod, getPatternMethod(), conflicts);
    }
    catch (IncorrectOperationException e) {}

    return showConflicts(conflicts, usages);
  }

  @NotNull
  protected UsageInfo[] findUsages() {
    final PsiManager manager = myMethod.getManager();
    final GlobalSearchScope searchScope = GlobalSearchScope.allScope(manager.getProject());
    final List<UsageInfo> usages = new ArrayList<>();
    for (PsiReference ref : ReferencesSearch.search(myMethod, searchScope, false)) {
      final PsiElement element = ref.getElement();
      if (element instanceof PsiReferenceExpression) {
        boolean isInternal = PsiTreeUtil.isAncestor(myMethod, element, true);
        usages.add(new MethodCallUsageInfo((PsiReferenceExpression)element, isInternal));
      }
      else if (element instanceof PsiDocTagValue) {
        usages.add(new JavadocUsageInfo((PsiDocTagValue)element));
      }
      else {
        throw new UnknownReferenceTypeException(element.getLanguage());
      }
    }

    if (myTargetClass.isInterface() && !PsiUtil.isLanguageLevel8OrHigher(myTargetClass)) {
      addInheritorUsages(myTargetClass, searchScope, usages);
    }

    final PsiCodeBlock body = myMethod.getBody();
    if (body != null) {
      body.accept(new JavaRecursiveElementWalkingVisitor() {
        @Override public void visitNewExpression(PsiNewExpression expression) {
          if (MoveInstanceMembersUtil.getClassReferencedByThis(expression) != null) {
            usages.add(new InternalUsageInfo(expression));
          }
          super.visitNewExpression(expression);
        }

        @Override public void visitReferenceExpression(PsiReferenceExpression expression) {
          if (MoveInstanceMembersUtil.getClassReferencedByThis(expression) != null) {
            usages.add(new InternalUsageInfo(expression));
          } else if (!expression.isQualified()) {
            final PsiElement resolved = expression.resolve();
            if (myTargetVariable.equals(resolved)) {
              usages.add(new InternalUsageInfo(expression));
            }
          }

          super.visitReferenceExpression(expression);
        }
      });
    }

    return usages.toArray(new UsageInfo[usages.size()]);
  }

  private static void addInheritorUsages(PsiClass aClass, final GlobalSearchScope searchScope, final List<UsageInfo> usages) {
    for (PsiClass inheritor : ClassInheritorsSearch.search(aClass, searchScope, false).findAll()) {
      if (!inheritor.isInterface()) {
        usages.add(new InheritorUsageInfo(inheritor));
      }
      else {
        addInheritorUsages(inheritor, searchScope, usages);
      }
    }
  }

  protected void refreshElements(@NotNull PsiElement[] elements) {
    LOG.assertTrue(elements.length == 3);
    myMethod = (PsiMethod) elements[0];
    myTargetVariable = (PsiVariable) elements[1];
    myTargetClass = (PsiClass) elements[2];
  }

  protected String getCommandName() {
    return RefactoringBundle.message("move.instance.method.command");
  }

  public PsiClass getTargetClass() {
    return myTargetClass;
  }

  protected void performRefactoring(@NotNull UsageInfo[] usages) {
    if (!CommonRefactoringUtil.checkReadOnlyStatus(myProject, myTargetClass)) return;

    PsiMethod patternMethod = createMethodToAdd();
    final List<PsiReference> docRefs = new ArrayList<>();
    for (UsageInfo usage : usages) {
      if (usage instanceof InheritorUsageInfo) {
        final PsiClass inheritor = ((InheritorUsageInfo)usage).getInheritor();
        addMethodToClass(inheritor, patternMethod, true);
      }
      else if (usage instanceof MethodCallUsageInfo && !((MethodCallUsageInfo)usage).isInternal()) {
        final PsiElement expression = ((MethodCallUsageInfo)usage).getMethodCallExpression();
        if (expression instanceof PsiMethodCallExpression) {
          correctMethodCall((PsiMethodCallExpression)expression, false);
        }
        else if (expression instanceof PsiMethodReferenceExpression) {
          PsiMethodReferenceExpression methodReferenceExpression = (PsiMethodReferenceExpression)expression;
          PsiExpression qualifierExpression = methodReferenceExpression.getQualifierExpression();
          String exprText;
          if (myTargetVariable instanceof PsiParameter ||
              qualifierExpression instanceof PsiReferenceExpression && ((PsiReferenceExpression)qualifierExpression).resolve() == myMethod.getContainingClass()) {
            exprText = myTargetVariable.getType().getCanonicalText();
          }
          else if (qualifierExpression instanceof PsiReferenceExpression) {
            exprText = qualifierExpression.getText() + "." + myTargetVariable.getName();
          }
          else {
            exprText = myTargetVariable.getName();
          }
          PsiExpression newQualifier = JavaPsiFacade.getInstance(myProject).getElementFactory().createExpressionFromText(exprText, null);
          ((PsiMethodReferenceExpression)expression).setQualifierExpression(
            (PsiExpression)JavaCodeStyleManager.getInstance(myProject).shortenClassReferences(newQualifier));
        }
      }
      else if (usage instanceof JavadocUsageInfo) {
        docRefs.add(usage.getElement().getReference());
      }
    }

    try {
      if (myTargetClass.isInterface()) {
        final PsiModifierList modifierList = patternMethod.getModifierList();
        if (!PsiUtil.isLanguageLevel8OrHigher(myTargetClass)) {
          patternMethod.getBody().delete();
          modifierList.setModifierProperty(PsiModifier.DEFAULT, false);
        }
        else {
          modifierList.setModifierProperty(PsiModifier.DEFAULT, true);
        }
        RefactoringUtil.makeMethodAbstract(myTargetClass, patternMethod);
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

  private void correctMethodCall(final PsiMethodCallExpression expression, final boolean isInternalCall) {
    try {
      final PsiManager manager = myMethod.getManager();
      PsiReferenceExpression methodExpression = expression.getMethodExpression();
      if (!methodExpression.isReferenceTo(myMethod)) return;
      final PsiExpression oldQualifier = methodExpression.getQualifierExpression();
      PsiExpression newQualifier = null;
      final PsiClass classReferencedByThis = MoveInstanceMembersUtil.getClassReferencedByThis(methodExpression);
      if (myTargetVariable instanceof PsiParameter) {
        final int index = myMethod.getParameterList().getParameterIndex((PsiParameter)myTargetVariable);
        final PsiExpression[] arguments = expression.getArgumentList().getExpressions();
        if (index < arguments.length) {
          newQualifier = (PsiExpression)arguments[index].copy();
          arguments[index].delete();
        }
      }
      else {
        VisibilityUtil.escalateVisibility((PsiField)myTargetVariable, expression);
        String newQualifierName = myTargetVariable.getName();
        if (myTargetVariable instanceof PsiField && oldQualifier != null) {
          final PsiClass aClass = PsiUtil.resolveClassInClassTypeOnly(oldQualifier.getType());
          if (aClass == ((PsiField)myTargetVariable).getContainingClass()) {
            newQualifierName = oldQualifier.getText() + "." + newQualifierName;
          }
        }
        newQualifier = JavaPsiFacade.getInstance(manager.getProject()).getElementFactory().createExpressionFromText(newQualifierName, null);
      }

      PsiExpression newArgument = null;

      if (classReferencedByThis != null) {
        @NonNls String thisArgumentText = null;
        if (manager.areElementsEquivalent(myMethod.getContainingClass(), classReferencedByThis)) {
          if (myOldClassParameterNames.containsKey(myMethod.getContainingClass())) {
            thisArgumentText = "this";
          }
        }
        else {
          thisArgumentText = classReferencedByThis.getName() + ".this";
        }

        if (thisArgumentText != null) {
          newArgument = JavaPsiFacade.getInstance(manager.getProject()).getElementFactory().createExpressionFromText(thisArgumentText, null);
        }
      } else {
        if (!isInternalCall && oldQualifier != null) {
          final PsiType type = oldQualifier.getType();
          if (type instanceof PsiClassType) {
            final PsiClass resolved = ((PsiClassType)type).resolve();
            if (resolved != null && getParameterNameToCreate(resolved) != null) {
              newArgument = replaceRefsToTargetVariable(oldQualifier);  //replace is needed in case old qualifier is e.g. the same as field as target variable
            }
          }
        }
      }


      if (newArgument != null) {
        expression.getArgumentList().add(newArgument);
      }

      if (newQualifier != null) {
        if (newQualifier instanceof PsiThisExpression && ((PsiThisExpression)newQualifier).getQualifier() == null) {
          //Remove now redundant 'this' qualifier
          if (oldQualifier != null) oldQualifier.delete();
        }
        else {
          final PsiReferenceExpression refExpr = (PsiReferenceExpression)JavaPsiFacade.getInstance(manager.getProject()).getElementFactory()
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

  private PsiExpression replaceRefsToTargetVariable(final PsiExpression expression) {
    final PsiManager manager = expression.getManager();
    if (expression instanceof PsiReferenceExpression &&
        ((PsiReferenceExpression)expression).isReferenceTo(myTargetVariable)) {
      return createThisExpr(manager);
    }

    expression.accept(new JavaRecursiveElementVisitor() {
      @Override public void visitReferenceExpression(PsiReferenceExpression expression) {
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

  private static PsiExpression createThisExpr(final PsiManager manager)  {
    try {
      return JavaPsiFacade.getInstance(manager.getProject()).getElementFactory().createExpressionFromText("this", null);
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
      return null;
    }
  }

  private static PsiMethod addMethodToClass(final PsiClass aClass, final PsiMethod patternMethod, boolean canAddOverride) {
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
    }

    return null;
  }

  private PsiMethod createMethodToAdd () {
    ChangeContextUtil.encodeContextInfo(myMethod, true);
    try {
      final PsiManager manager = myMethod.getManager();
      final PsiElementFactory factory = JavaPsiFacade.getInstance(manager.getProject()).getElementFactory();

      //correct internal references
      final PsiCodeBlock body = myMethod.getBody();
      if (body != null) {
        final Map<PsiElement, PsiElement> replaceMap = new HashMap<>();
        body.accept(new JavaRecursiveElementVisitor() {
          @Override public void visitThisExpression(PsiThisExpression expression) {
            final PsiClass classReferencedByThis = MoveInstanceMembersUtil.getClassReferencedByThis(expression);
            if (classReferencedByThis != null && !PsiTreeUtil.isAncestor(myMethod, classReferencedByThis, false)) {
              final PsiElementFactory factory = JavaPsiFacade.getInstance(myProject).getElementFactory();
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

          @Override public void visitReferenceExpression(PsiReferenceExpression expression) {
            try {
              final PsiExpression qualifier = expression.getQualifierExpression();
              final PsiElement resolved = expression.resolve();
              if (qualifier instanceof PsiReferenceExpression && ((PsiReferenceExpression)qualifier).isReferenceTo(myTargetVariable)) {
                if (resolved instanceof PsiField) {
                  for (PsiParameter parameter : myMethod.getParameterList().getParameters()) {
                    if (Comparing.strEqual(parameter.getName(), ((PsiField)resolved).getName())) {
                      qualifier.replace(factory.createExpressionFromText("this", null));
                      return;
                    }
                  }
                }
                //Target is a field, replace target.m -> m
                qualifier.delete();
                return;
              }
              if (myTargetVariable.equals(resolved)) {
                PsiThisExpression thisExpression = RefactoringChangeUtil.createThisExpression(manager, PsiTreeUtil.isAncestor(myMethod,
                                                                                                                              PsiTreeUtil
                                                                                                                                .getParentOfType(
                                                                                                                                  expression,
                                                                                                                                  PsiClass.class),
                                                                                                                              true)
                                                                                                       ? myTargetClass
                                                                                                       : null);
                replaceMap.put(expression, thisExpression);
                return;
              }
              else if (myMethod.equals(resolved)) {
              }
              else {
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

          @Override public void visitNewExpression(PsiNewExpression expression) {
            try {
              final PsiExpression qualifier = expression.getQualifier();
              if (qualifier instanceof PsiReferenceExpression && ((PsiReferenceExpression)qualifier).isReferenceTo(myTargetVariable)) {
                //Target is a field, replace target.new A() -> new A()
                qualifier.delete();
              } else {
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

          @Override public void visitMethodCallExpression(PsiMethodCallExpression expression) {
            correctMethodCall(expression, true);
            super.visitMethodCallExpression(expression);
          }
        });
        for (PsiElement element : replaceMap.keySet()) {
          final PsiElement replacement = replaceMap.get(element);
          element.replace(replacement);
        }
      }

      final PsiMethod methodCopy = getPatternMethod();

      final List<PsiParameter> newParameters = Arrays.asList(methodCopy.getParameterList().getParameters());
      RefactoringUtil.fixJavadocsForParams(methodCopy, new HashSet<>(newParameters));
      return methodCopy;
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
      return myMethod;
    }
  }

  private PsiMethod getPatternMethod() throws IncorrectOperationException {
    final PsiMethod methodCopy = (PsiMethod)myMethod.copy();
    String name = myTargetClass.isInterface()
                  ? PsiModifier.PUBLIC :
                  !Comparing.strEqual(myNewVisibility, VisibilityUtil.ESCALATE_VISIBILITY) ? myNewVisibility : null;
    if (name != null) {
      PsiUtil.setModifierProperty(methodCopy, name, true);
    }
    if (myTargetVariable instanceof PsiParameter) {
      final int index = myMethod.getParameterList().getParameterIndex((PsiParameter)myTargetVariable);
      methodCopy.getParameterList().getParameters()[index].delete();
    }

    addParameters(JavaPsiFacade.getInstance(myProject).getElementFactory(), methodCopy, myTargetClass.isInterface());
    return methodCopy;
  }

  private void addParameters(final PsiElementFactory factory, final PsiMethod methodCopy, final boolean isInterface) throws IncorrectOperationException {
    final Set<Map.Entry<PsiClass, String>> entries = myOldClassParameterNames.entrySet();
    for (final Map.Entry<PsiClass, String> entry : entries) {
      final PsiClassType type = factory.createType(entry.getKey());
      final PsiParameter parameter = factory.createParameter(entry.getValue(), type);
      if (isInterface) {
        PsiUtil.setModifierProperty(parameter, PsiModifier.FINAL, false);
      }
      methodCopy.getParameterList().add(parameter);
    }
  }

  private String getParameterNameToCreate(@NotNull PsiClass aClass) {
    return myOldClassParameterNames.get(aClass);
  }
}
