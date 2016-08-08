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
package com.intellij.refactoring.inline;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.rename.NonCodeUsageInfoFactory;
import com.intellij.refactoring.util.TextOccurrencesUtil;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewDescriptor;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author yole
 */
public class InlineToAnonymousClassProcessor extends BaseRefactoringProcessor {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.inline.InlineToAnonymousClassProcessor");

  private PsiClass myClass;
  private final PsiCall myCallToInline;
  private final boolean myInlineThisOnly;
  private final boolean mySearchInComments;
  private final boolean mySearchInNonJavaFiles;

  protected InlineToAnonymousClassProcessor(Project project,
                                            PsiClass psiClass,
                                            @Nullable final PsiCall callToInline,
                                            boolean inlineThisOnly,
                                            final boolean searchInComments,
                                            final boolean searchInNonJavaFiles) {
    super(project);
    myClass = psiClass;
    myCallToInline = callToInline;
    myInlineThisOnly = inlineThisOnly;
    if (myInlineThisOnly) assert myCallToInline != null;
    mySearchInComments = searchInComments;
    mySearchInNonJavaFiles = searchInNonJavaFiles;
  }

  @NotNull
  protected UsageViewDescriptor createUsageViewDescriptor(@NotNull UsageInfo[] usages) {
    return new InlineViewDescriptor(myClass);
  }

  @NotNull
  protected UsageInfo[] findUsages() {
    if (myInlineThisOnly) {
      return new UsageInfo[] { new UsageInfo(myCallToInline) };
    }
    Set<UsageInfo> usages = new HashSet<>();
    for (PsiReference reference : ReferencesSearch.search(myClass)) {
      usages.add(new UsageInfo(reference.getElement()));
    }

    final String qName = myClass.getQualifiedName();
    if (qName != null) {
      List<UsageInfo> nonCodeUsages = new ArrayList<>();
      if (mySearchInComments) {
        TextOccurrencesUtil.addUsagesInStringsAndComments(myClass, qName, nonCodeUsages,
                                                      new NonCodeUsageInfoFactory(myClass, qName));
      }

      if (mySearchInNonJavaFiles) {
        GlobalSearchScope projectScope = GlobalSearchScope.projectScope(myClass.getProject());
        TextOccurrencesUtil.addTextOccurences(myClass, qName, projectScope, nonCodeUsages,
                                          new NonCodeUsageInfoFactory(myClass, qName));
      }
      usages.addAll(nonCodeUsages);
    }

    return usages.toArray(new UsageInfo[usages.size()]);
  }

  protected void refreshElements(@NotNull PsiElement[] elements) {
    assert elements.length == 1;
    myClass = (PsiClass) elements [0];
  }

  protected boolean isPreviewUsages(@NotNull UsageInfo[] usages) {
    if (super.isPreviewUsages(usages)) return true;
    for(UsageInfo usage: usages) {
      if (isForcePreview(usage)) {
        WindowManager.getInstance().getStatusBar(myProject).setInfo(RefactoringBundle.message("occurrences.found.in.comments.strings.and.non.java.files"));
        return true;
      }
    }
    return false;
  }

  private static boolean isForcePreview(final UsageInfo usage) {
    if (usage.isNonCodeUsage) return true;
    PsiElement element = usage.getElement();
    if (element != null) {
      PsiFile file = element.getContainingFile();
      if (!(file instanceof PsiJavaFile)) {
        return true;
      }
    }
    return false;
  }

  protected boolean preprocessUsages(@NotNull final Ref<UsageInfo[]> refUsages) {
    MultiMap<PsiElement, String> conflicts = getConflicts(refUsages.get());
    if (!conflicts.isEmpty()) {
      return showConflicts(conflicts, refUsages.get());
    }
    return super.preprocessUsages(refUsages);
  }

  public MultiMap<PsiElement, String> getConflicts(final UsageInfo[] usages) {
    final MultiMap<PsiElement, String> result = new MultiMap<>();
    ReferencedElementsCollector collector = new ReferencedElementsCollector() {
      protected void checkAddMember(@NotNull final PsiMember member) {
        if (PsiTreeUtil.isAncestor(myClass, member, false)) {
          return;
        }
        final PsiModifierList modifierList = member.getModifierList();
        if (member.getContainingClass() == myClass.getSuperClass() && modifierList != null &&
            modifierList.hasModifierProperty(PsiModifier.PROTECTED)) {
          // ignore access to protected members of superclass - they'll be accessible anyway
          return;
        }
        super.checkAddMember(member);
      }
    };
    InlineMethodProcessor.addInaccessibleMemberConflicts(myClass, usages, collector, result);
    myClass.accept(new JavaRecursiveElementVisitor(){
      @Override
      public void visitParameter(PsiParameter parameter) {
        super.visitParameter(parameter);
        if (PsiUtil.resolveClassInType(parameter.getType()) != myClass) return;

        for (PsiReference psiReference : ReferencesSearch.search(parameter)) {
          final PsiElement refElement = psiReference.getElement();
          if (refElement instanceof PsiExpression) {
            final PsiReferenceExpression referenceExpression = PsiTreeUtil.getParentOfType(refElement, PsiReferenceExpression.class);
            if (referenceExpression != null && referenceExpression.getQualifierExpression() == refElement) {
              final PsiElement resolvedMember = referenceExpression.resolve();
              if (resolvedMember != null && PsiTreeUtil.isAncestor(myClass, resolvedMember, false)) {
                if (resolvedMember instanceof PsiMethod) {
                  if (myClass.findMethodsBySignature((PsiMethod)resolvedMember, true).length > 1) { //skip inherited methods
                    continue;
                  }
                }
                result.putValue(refElement, "Class cannot be inlined because a call to its member inside body");
              }
            }
          }
        }
      }

      @Override
      public void visitNewExpression(PsiNewExpression expression) {
        super.visitNewExpression(expression);
        if (PsiUtil.resolveClassInType(expression.getType()) != myClass) return;
        result.putValue(expression, "Class cannot be inlined because a call to its constructor inside body");
      }

      @Override
      public void visitMethodCallExpression(PsiMethodCallExpression expression) {
        super.visitMethodCallExpression(expression);
        final PsiReferenceExpression methodExpression = expression.getMethodExpression();
        final PsiExpression qualifierExpression = methodExpression.getQualifierExpression();
        if (qualifierExpression != null && PsiUtil.resolveClassInType(qualifierExpression.getType()) != myClass) return;
        final PsiElement resolved = methodExpression.resolve();
        if (resolved instanceof PsiMethod) {
          final PsiMethod method = (PsiMethod)resolved;
          if ("getClass".equals(method.getName()) && method.getParameterList().getParametersCount() == 0) {
            result.putValue(methodExpression, "Result of getClass() invocation would be changed");
          }
        }
      }
    });
    return result;
  }

  protected void performRefactoring(@NotNull UsageInfo[] usages) {
    final PsiClassType superType = getSuperType(myClass);
    LOG.assertTrue(superType != null);
    List<PsiElement> elementsToDelete = new ArrayList<>();
    List<PsiNewExpression> newExpressions = new ArrayList<>();
    for(UsageInfo info: usages) {
      final PsiElement element = info.getElement();
      if (element instanceof PsiNewExpression) {
        newExpressions.add((PsiNewExpression)element);
      }
      else if (element.getParent() instanceof PsiNewExpression) {
        newExpressions.add((PsiNewExpression) element.getParent());
      }
      else {
        PsiImportStatement statement = PsiTreeUtil.getParentOfType(element, PsiImportStatement.class);
        if (statement != null && !myInlineThisOnly) {
          elementsToDelete.add(statement);
        }
        else {
          PsiTypeElement typeElement = PsiTreeUtil.getParentOfType(element, PsiTypeElement.class);
          if (typeElement != null) {
            replaceWithSuperType(typeElement, superType);
          }
        }
      }
    }

    Collections.sort(newExpressions, PsiUtil.BY_POSITION);
    for(PsiNewExpression newExpression: newExpressions) {
      replaceNewOrType(newExpression, superType);
    }

    for(PsiElement element: elementsToDelete) {
      try {
        if (element.isValid()) {
          element.delete();
        }
      }
      catch (IncorrectOperationException e) {
        LOG.error(e);
      }
    }
    if (!myInlineThisOnly) {
      try {
        myClass.delete();
      }
      catch(IncorrectOperationException e) {
        LOG.error(e);
      }
    }
  }

  private void replaceNewOrType(final PsiNewExpression psiNewExpression, final PsiClassType superType) {
    try {
      if (psiNewExpression.getArrayDimensions().length == 0 && psiNewExpression.getArrayInitializer() == null) {
        new InlineToAnonymousConstructorProcessor(myClass, psiNewExpression, superType).run();
      }
      else {
        PsiClass target = superType.resolve();
        assert target != null : superType;
        PsiElementFactory factory = JavaPsiFacade.getInstance(myClass.getProject()).getElementFactory();
        PsiJavaCodeReferenceElement element = factory.createClassReferenceElement(target);
        PsiJavaCodeReferenceElement reference = psiNewExpression.getClassReference();
        assert reference != null : psiNewExpression;
        reference.replace(element);
      }
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  private void replaceWithSuperType(final PsiTypeElement typeElement, final PsiClassType superType) {
    PsiElementFactory factory = JavaPsiFacade.getInstance(myClass.getProject()).getElementFactory();
    PsiClassType psiType = (PsiClassType) typeElement.getType();
    PsiClassType.ClassResolveResult classResolveResult = psiType.resolveGenerics();
    PsiType substType = classResolveResult.getSubstitutor().substitute(superType);
    assert classResolveResult.getElement() == myClass;
    try {
      PsiElement replaced = typeElement.replace(factory.createTypeElement(substType));
      JavaCodeStyleManager.getInstance(myProject).shortenClassReferences(replaced);
    }
    catch(IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  @Nullable
  public static PsiClassType getSuperType(final PsiClass aClass) {
    PsiElementFactory factory = JavaPsiFacade.getInstance(aClass.getProject()).getElementFactory();

    PsiClassType superType;
    PsiClass superClass = aClass.getSuperClass();
    if (superClass == null) {
      //java.lang.Object was not found
      return null;
    }
    PsiClassType[] interfaceTypes = aClass.getImplementsListTypes();
    if (interfaceTypes.length > 0 && !InlineToAnonymousClassHandler.isRedundantImplements(superClass, interfaceTypes [0])) {
      superType = interfaceTypes [0];
    }
    else {
      PsiClassType[] classTypes = aClass.getExtendsListTypes();
      if (classTypes.length > 0) {
        superType = classTypes [0];
      }
      else {
        superType = factory.createType(superClass);
      }
    }
    return superType;
  }

  protected String getCommandName() {
    return RefactoringBundle.message("inline.to.anonymous.command.name", myClass.getQualifiedName());
  }

}
