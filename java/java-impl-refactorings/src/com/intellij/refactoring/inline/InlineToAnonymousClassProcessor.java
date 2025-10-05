// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.inline;

import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
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
import com.intellij.refactoring.util.RefactoringUIUtil;
import com.intellij.refactoring.util.TextOccurrencesUtil;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewDescriptor;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.MultiMap;
import com.siyeh.ig.psiutils.SealedUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class InlineToAnonymousClassProcessor extends BaseRefactoringProcessor {
  private static final Logger LOG = Logger.getInstance(InlineToAnonymousClassProcessor.class);

  private PsiClass myClass;
  private final PsiCall myCallToInline;
  private final boolean myInlineThisOnly;
  private final boolean mySearchInComments;
  private final boolean mySearchInNonJavaFiles;

  public InlineToAnonymousClassProcessor(Project project,
                                         PsiClass psiClass,
                                         @Nullable PsiCall callToInline,
                                         boolean inlineThisOnly,
                                         boolean searchInComments,
                                         boolean searchInNonJavaFiles) {
    super(project);
    myClass = psiClass;
    myCallToInline = callToInline;
    myInlineThisOnly = inlineThisOnly;
    assert !myInlineThisOnly || myCallToInline != null;
    mySearchInComments = searchInComments;
    mySearchInNonJavaFiles = searchInNonJavaFiles;
  }

  @Override
  protected @NotNull UsageViewDescriptor createUsageViewDescriptor(UsageInfo @NotNull [] usages) {
    return new InlineViewDescriptor(myClass);
  }

  @Override
  public UsageInfo @NotNull [] findUsages() {
    if (myInlineThisOnly) {
      return new UsageInfo[] { new UsageInfo(myCallToInline) };
    }
    Set<UsageInfo> usages = new HashSet<>();
    for (PsiReference reference : ReferencesSearch.search(myClass, myRefactoringScope).asIterable()) {
      usages.add(new UsageInfo(reference.getElement()));
    }

    final String qName = myClass.getQualifiedName();
    if (qName != null) {
      List<UsageInfo> nonCodeUsages = new ArrayList<>();
      if (mySearchInComments) {
        TextOccurrencesUtil.addUsagesInStringsAndComments(myClass, myRefactoringScope, qName, nonCodeUsages,
                                                          new NonCodeUsageInfoFactory(myClass, qName));
      }

      if (mySearchInNonJavaFiles && myRefactoringScope instanceof GlobalSearchScope scope) {
        TextOccurrencesUtil.addTextOccurrences(myClass, qName, scope, nonCodeUsages, new NonCodeUsageInfoFactory(myClass, qName));
      }
      usages.addAll(nonCodeUsages);
    }

    return usages.toArray(UsageInfo.EMPTY_ARRAY);
  }

  @Override
  protected @NotNull Collection<? extends PsiElement> getElementsToWrite(@NotNull UsageViewDescriptor descriptor) {
    if (!myInlineThisOnly && !myClass.isWritable()) {
      return Collections.emptyList();
    }
    return super.getElementsToWrite(descriptor);
  }

  @Override
  protected void refreshElements(PsiElement @NotNull [] elements) {
    assert elements.length == 1;
    myClass = (PsiClass) elements [0];
  }

  @Override
  protected boolean isPreviewUsages(UsageInfo @NotNull [] usages) {
    if (super.isPreviewUsages(usages)) return true;
    for (UsageInfo usage: usages) {
      if (isForcePreview(usage)) {
        WindowManager.getInstance().getStatusBar(myProject).setInfo(RefactoringBundle.message("occurrences.found.in.comments.strings.and.non.java.files"));
        return true;
      }
    }
    return false;
  }

  private static boolean isForcePreview(UsageInfo usage) {
    if (usage.isNonCodeUsage) return true;
    PsiElement element = usage.getElement();
    return element != null && !(element.getContainingFile() instanceof PsiJavaFile);
  }

  @Override
  protected boolean preprocessUsages(@NotNull Ref<UsageInfo[]> refUsages) {
    MultiMap<PsiElement, String> conflicts = getConflicts(refUsages.get());
    if (!conflicts.isEmpty()) {
      return showConflicts(conflicts, refUsages.get());
    }
    return super.preprocessUsages(refUsages);
  }

  public MultiMap<PsiElement, String> getConflicts(UsageInfo[] usages) {
    final MultiMap<PsiElement, String> result = new MultiMap<>();
    ReferencedElementsCollector collector = new ReferencedElementsCollector() {
      @Override
      protected void checkAddMember(@NotNull PsiMember member) {
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
    addInaccessibleMemberConflicts(usages, collector, result);
    myClass.accept(new JavaRecursiveElementVisitor() {
      @Override
      public void visitParameter(@NotNull PsiParameter parameter) {
        super.visitParameter(parameter);
        if (!myClass.isEquivalentTo(PsiUtil.resolveClassInType(parameter.getType()))) return;

        for (PsiReference psiReference : ReferencesSearch.search(parameter).asIterable()) {
          final PsiElement refElement = psiReference.getElement();
          if (refElement instanceof PsiExpression) {
            final PsiReferenceExpression referenceExpression = PsiTreeUtil.getParentOfType(refElement, PsiReferenceExpression.class);
            if (referenceExpression != null && referenceExpression.getQualifierExpression() == refElement) {
              final PsiElement resolvedMember = referenceExpression.resolve();
              if (resolvedMember != null && PsiTreeUtil.isAncestor(myClass, resolvedMember, false)) {
                if (resolvedMember instanceof PsiMethod method && myClass.findMethodsBySignature(method, true).length > 1) { 
                  //skip inherited methods
                  continue;
                }
                result.putValue(refElement, JavaRefactoringBundle.message("inline.to.anonymous.no.method.calls"));
              }
            }
          }
        }
      }

      @Override
      public void visitNewExpression(@NotNull PsiNewExpression expression) {
        super.visitNewExpression(expression);
        if (!myClass.isEquivalentTo(PsiUtil.resolveClassInType(expression.getType()))) return;
        result.putValue(expression, JavaRefactoringBundle.message("inline.to.anonymous.no.ctor.calls"));
      }

      @Override
      public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
        super.visitMethodCallExpression(expression);
        final PsiReferenceExpression methodExpression = expression.getMethodExpression();
        final PsiExpression qualifierExpression = methodExpression.getQualifierExpression();
        if (qualifierExpression != null && !myClass.isEquivalentTo(PsiUtil.resolveClassInType(qualifierExpression.getType()))) return;
        final PsiElement resolved = methodExpression.resolve();
        if (resolved instanceof PsiMethod method && "getClass".equals(method.getName()) && method.getParameterList().isEmpty()) {
          result.putValue(methodExpression, JavaRefactoringBundle.message("inline.to.anonymous.no.get.class.calls"));
        }
      }
    });
    return result;
  }

  private void addInaccessibleMemberConflicts(UsageInfo[] usages,
                                              ReferencedElementsCollector collector,
                                              MultiMap<PsiElement, @NlsContexts.DialogMessage String> conflicts) {
    PsiElement element = myClass.getLBrace();
    while (element != null) {
      element.accept(collector);
      element = element.getNextSibling();
    }
    final Map<PsiMember, Set<PsiMember>> containersToReferenced = 
      InlineMethodProcessor.getInaccessible(collector.myReferencedMembers, usages, myClass);
    String classDescription = RefactoringUIUtil.getDescription(myClass, true);

    containersToReferenced.forEach((container, inaccessibles) -> {
      for (PsiMember inaccessible : inaccessibles) {
        final String referencedDescription = RefactoringUIUtil.getDescription(inaccessible, true);
        final String containerDescription = RefactoringUIUtil.getDescription(container, true);
        String message = RefactoringBundle.message("0.which.is.used.in.1.not.accessible.from.call.site.s.in.2",
                                                   referencedDescription, classDescription, containerDescription);
        conflicts.putValue(usages.length == 1 ? inaccessible : container, StringUtil.capitalize(message));
      }
    });
  }

  @Override
  protected void performRefactoring(UsageInfo @NotNull [] usages) {
    final PsiClassType superType = getSuperType(myClass);
    LOG.assertTrue(superType != null);
    List<PsiElement> elementsToDelete = new ArrayList<>();
    List<PsiNewExpression> newExpressions = new ArrayList<>();
    for (UsageInfo info : usages) {
      final PsiElement element = info.getElement();
      if (element instanceof PsiNewExpression exp) {
        newExpressions.add(exp);
      }
      else if (element != null && element.getParent() instanceof PsiNewExpression exp) {
        newExpressions.add(exp);
      }
      else if (element instanceof PsiJavaCodeReferenceElement && element.getParent() instanceof PsiReferenceList refList) {
        if (refList.getParent() instanceof PsiClass parentClass && refList == parentClass.getPermitsList()) {
          SealedUtils.removeFromPermitsList(parentClass, myClass);
        }
      }
      else {
        PsiImportStatement statement = PsiTreeUtil.getParentOfType(element, PsiImportStatement.class);
        if (statement != null && !myInlineThisOnly) {
          elementsToDelete.add(statement);
        }
        else if (element instanceof PsiJavaCodeReferenceElement ref) {
            replaceWithSuperType(ref, superType);
          }
      }
    }

    newExpressions.sort(PsiUtil.BY_POSITION);
    for (PsiNewExpression newExpression : newExpressions) {
      replaceNewOrType(newExpression, superType);
    }

    for (PsiElement element : elementsToDelete) {
      try {
        if (element.isValid()) {
          element.delete();
        }
      }
      catch (IncorrectOperationException e) {
        LOG.error(e);
      }
    }
    if (!myInlineThisOnly && myClass.getOriginalElement().isWritable()) {
      try {
        myClass.delete();
      }
      catch(IncorrectOperationException e) {
        LOG.error(e);
      }
    }
  }

  private void replaceNewOrType(PsiNewExpression psiNewExpression, PsiClassType superType) {
    try {
      if (!psiNewExpression.isArrayCreation()) {
        new InlineToAnonymousConstructorProcessor(myClass, psiNewExpression, superType).run();
      }
      else {
        PsiClass target = superType.resolve();
        assert target != null : superType;
        PsiElementFactory factory = JavaPsiFacade.getElementFactory(myProject);
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

  private void replaceWithSuperType(PsiJavaCodeReferenceElement ref, PsiClassType superType) {
    PsiTypeElement typeElement = PsiTreeUtil.getParentOfType(ref, PsiTypeElement.class);
    if (typeElement == null) return;
    PsiClassType type = (PsiClassType) typeElement.getType();
    PsiClassType.ClassResolveResult classResolveResult = type.resolveGenerics();
    PsiClassType substType = (PsiClassType)classResolveResult.getSubstitutor().substitute(superType);
    try {
      PsiJavaCodeReferenceElement replacement = JavaPsiFacade.getElementFactory(myProject).createReferenceElementByType(substType);
      while (!myClass.isEquivalentTo(ref.resolve()) && ref.getQualifier() instanceof PsiJavaCodeReferenceElement qRef) {
        ref = qRef;
      }
      PsiElement replaced = ref.replace(replacement);
      JavaCodeStyleManager.getInstance(myProject).shortenClassReferences(replaced);
    }
    catch(IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  public static @Nullable PsiClassType getSuperType(PsiClass aClass) {
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(aClass.getProject());

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
      superType = classTypes.length > 0 ? classTypes[0] : factory.createType(superClass);
    }
    return superType;
  }

  @Override
  protected @NotNull String getCommandName() {
    return JavaRefactoringBundle.message("inline.to.anonymous.command.name", myClass.getQualifiedName());
  }
}
