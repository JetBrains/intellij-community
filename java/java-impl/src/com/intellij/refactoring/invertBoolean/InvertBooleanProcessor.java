/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.refactoring.invertBoolean;

import com.intellij.codeInsight.CodeInsightServicesUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.*;
import com.intellij.psi.search.searches.MethodReferencesSearch;
import com.intellij.psi.search.searches.OverridingMethodsSearch;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.rename.RenameProcessor;
import com.intellij.refactoring.rename.RenameUtil;
import com.intellij.refactoring.util.MoveRenameUsageInfo;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewDescriptor;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.Query;
import com.intellij.util.containers.HashMap;
import com.intellij.util.containers.HashSet;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * @author ven
 */
public class InvertBooleanProcessor extends BaseRefactoringProcessor {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.invertBoolean.InvertBooleanMethodProcessor");

  private PsiNamedElement myElement;
  private final String myNewName;
  private final RenameProcessor myRenameProcessor;
  private final Map<UsageInfo, SmartPsiElementPointer> myToInvert = new HashMap<UsageInfo, SmartPsiElementPointer>();
  private final SmartPointerManager mySmartPointerManager;

  public InvertBooleanProcessor(final PsiNamedElement namedElement, final String newName) {
    super(namedElement.getProject());
    myElement = namedElement;
    myNewName = newName;
    final Project project = namedElement.getProject();
    myRenameProcessor = new RenameProcessor(project, namedElement, newName, false, false);
    mySmartPointerManager = SmartPointerManager.getInstance(project);
  }

  @Override
  @NotNull
  protected UsageViewDescriptor createUsageViewDescriptor(UsageInfo[] usages) {
    return new InvertBooleanUsageViewDescriptor(myElement);
  }

  @Override
  protected boolean preprocessUsages(Ref<UsageInfo[]> refUsages) {
    final MultiMap<PsiElement, String> conflicts = new MultiMap<PsiElement, String>();
    for (UsageInfo info : myToInvert.keySet()) {
      final PsiElement element = info.getElement();
      if (element instanceof PsiMethodReferenceExpression) {
        conflicts.putValue(element, "Method is used in method reference expression");
      }
    }

    if (!conflicts.isEmpty())  {
      return showConflicts(conflicts, null);
    }

    if (myRenameProcessor.preprocessUsages(refUsages)) {
      prepareSuccessful();
      return true;
    }
    return false;
  }

  @Override
  @NotNull
  protected UsageInfo[] findUsages() {
    final List<SmartPsiElementPointer> toInvert = new ArrayList<SmartPsiElementPointer>();

    addRefsToInvert(toInvert, myElement);

    if (myElement instanceof PsiMethod) {
      final Collection<PsiMethod> overriders = OverridingMethodsSearch.search((PsiMethod)myElement).findAll();
      for (PsiMethod overrider : overriders) {
        myRenameProcessor.addElement(overrider, myNewName);
      }

      Collection<PsiMethod> allMethods = new HashSet<PsiMethod>(overriders);
      allMethods.add((PsiMethod)myElement);

      for (PsiMethod method : allMethods) {
        method.accept(new JavaRecursiveElementWalkingVisitor() {
          @Override public void visitReturnStatement(PsiReturnStatement statement) {
            final PsiExpression returnValue = statement.getReturnValue();
            if (returnValue != null && PsiType.BOOLEAN.equals(returnValue.getType())) {
              toInvert.add(mySmartPointerManager.createSmartPsiElementPointer(returnValue));
            }
          }

          @Override
          public void visitClass(PsiClass aClass) {
          }
        });
      }
    } else if (myElement instanceof PsiParameter && ((PsiParameter)myElement).getDeclarationScope() instanceof PsiMethod) {
      final PsiMethod method = (PsiMethod)((PsiParameter)myElement).getDeclarationScope();
      int index = method.getParameterList().getParameterIndex((PsiParameter)myElement);
      LOG.assertTrue(index >= 0);
      final Query<PsiReference> methodQuery = MethodReferencesSearch.search(method);
      final Collection<PsiReference> methodRefs = methodQuery.findAll();
      for (PsiReference ref : methodRefs) {
        PsiElement parent = ref.getElement().getParent();
        if (parent instanceof PsiAnonymousClass) {
          parent = parent.getParent();
        }
        if (parent instanceof PsiCall) {
          final PsiCall call = (PsiCall)parent;
          final PsiReferenceExpression methodExpression = call instanceof PsiMethodCallExpression ?
                                                          ((PsiMethodCallExpression)call).getMethodExpression() :
                                                          null;
          final PsiExpressionList argumentList = call.getArgumentList();
          if (argumentList != null) {
            final PsiExpression[] args = argumentList.getExpressions();
            if (index < args.length) {
              if (methodExpression == null || methodExpression.getQualifier() == null || !"super".equals(methodExpression.getQualifierExpression().getText())) {
                toInvert.add(mySmartPointerManager.createSmartPsiElementPointer(args[index]));
              }
            }
          }
        }
      }
      final Collection<PsiMethod> overriders = OverridingMethodsSearch.search(method).findAll();
      for (PsiMethod overrider : overriders) {
        final PsiParameter overriderParameter = overrider.getParameterList().getParameters()[index];
        myRenameProcessor.addElement(overriderParameter, myNewName);
        addRefsToInvert(toInvert, overriderParameter);
      }
    }

    final UsageInfo[] renameUsages = myRenameProcessor.findUsages();

    final SmartPsiElementPointer[] usagesToInvert = toInvert.toArray(new SmartPsiElementPointer[toInvert.size()]);

    //merge rename and invert usages
    Map<PsiElement, UsageInfo> expressionsToUsages = new HashMap<PsiElement, UsageInfo>();
    List<UsageInfo> result = new ArrayList<UsageInfo>();
    for (UsageInfo renameUsage : renameUsages) {
      expressionsToUsages.put(renameUsage.getElement(), renameUsage);
      result.add(renameUsage);
    }

    for (SmartPsiElementPointer pointer : usagesToInvert) {
      final PsiExpression expression = (PsiExpression)pointer.getElement();
      if (!expressionsToUsages.containsKey(expression)) {
        final UsageInfo usageInfo = new UsageInfo(expression);
        expressionsToUsages.put(expression, usageInfo);
        result.add(usageInfo); //fake UsageInfo
        myToInvert.put(usageInfo, pointer);
      } else {
        myToInvert.put(expressionsToUsages.get(expression), pointer);
      }
    }

    return result.toArray(new UsageInfo[result.size()]);
  }

  private void addRefsToInvert(final List<SmartPsiElementPointer> toInvert, final PsiNamedElement namedElement) {
    final Query<PsiReference> query = namedElement instanceof PsiMethod ?
                                      MethodReferencesSearch.search((PsiMethod)namedElement) :
                                      ReferencesSearch.search(namedElement);
    final Collection<PsiReference> refs = query.findAll();

    for (PsiReference ref : refs) {
      final PsiElement element = ref.getElement();
      if (element instanceof PsiReferenceExpression) {
        final PsiReferenceExpression refExpr = (PsiReferenceExpression)element;
        PsiElement parent = refExpr.getParent();
        if (parent instanceof PsiAssignmentExpression && refExpr.equals(((PsiAssignmentExpression)parent).getLExpression())) {
          toInvert.add(mySmartPointerManager.createSmartPsiElementPointer(((PsiAssignmentExpression)parent).getRExpression()));
        }
        else {
          if (namedElement instanceof PsiParameter) { //filter usages in super method calls
            if (refExpr.getParent().getParent() instanceof PsiMethodCallExpression) {
              final PsiReferenceExpression methodExpression = ((PsiMethodCallExpression)refExpr.getParent().getParent()).getMethodExpression();
              if (methodExpression.getQualifier() != null && "super".equals(methodExpression.getQualifierExpression().getText())) {
                continue;
              }
            }
          }

          toInvert.add(mySmartPointerManager.createSmartPsiElementPointer(refExpr));
        }
      }
    }

    if (namedElement instanceof PsiVariable) {
      final PsiExpression initializer = ((PsiVariable)namedElement).getInitializer();
      if (initializer != null) {
        toInvert.add(mySmartPointerManager.createSmartPsiElementPointer(initializer));
      }
    }
  }

  @Override
  protected void refreshElements(PsiElement[] elements) {
    LOG.assertTrue(elements.length == 1 && elements[0] instanceof PsiMethod);
    myElement = (PsiMethod)elements[0];
  }

  private static UsageInfo[] extractUsagesForElement(PsiElement element, UsageInfo[] usages) {
    final ArrayList<UsageInfo> extractedUsages = new ArrayList<UsageInfo>(usages.length);
    for (UsageInfo usage : usages) {
      if (usage instanceof MoveRenameUsageInfo) {
        MoveRenameUsageInfo usageInfo = (MoveRenameUsageInfo)usage;
        if (element.equals(usageInfo.getReferencedElement())) {
          extractedUsages.add(usageInfo);
        }
      }
    }
    return extractedUsages.toArray(new UsageInfo[extractedUsages.size()]);
  }


  @Override
  protected void performRefactoring(UsageInfo[] usages) {
    for (final PsiElement element : myRenameProcessor.getElements()) {
      try {
        RenameUtil.doRename(element, myRenameProcessor.getNewName(element), extractUsagesForElement(element, usages), myProject, null);
      }
      catch (final IncorrectOperationException e) {
        RenameUtil.showErrorMessage(e, element, myProject);
        return;
      }
    }


    for (UsageInfo usage : usages) {
      final SmartPsiElementPointer pointerToInvert = myToInvert.get(usage);
      if (pointerToInvert != null) {
        PsiExpression expression = (PsiExpression)pointerToInvert.getElement();
        LOG.assertTrue(expression != null);
        if (expression.getParent() instanceof PsiMethodCallExpression) expression = (PsiExpression)expression.getParent();
        try {
          while (expression.getParent() instanceof PsiPrefixExpression &&
                 ((PsiPrefixExpression)expression.getParent()).getOperationTokenType() == JavaTokenType.EXCL) {
            expression = (PsiExpression)expression.getParent();
          }
          if (!(expression.getParent() instanceof PsiExpressionStatement)) {
            expression.replace(CodeInsightServicesUtil.invertCondition(expression));
          }
        }
        catch (IncorrectOperationException e) {
          LOG.error(e);
        }
      }
    }
  }

  @Override
  protected String getCommandName() {
    return InvertBooleanHandler.REFACTORING_NAME;
  }
}
