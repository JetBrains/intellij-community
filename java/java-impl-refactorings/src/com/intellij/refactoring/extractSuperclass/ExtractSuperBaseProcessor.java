// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.extractSuperclass;

import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.MethodSignatureUtil;
import com.intellij.refactoring.turnRefsToSuper.TurnRefsToSuperProcessorBase;
import com.intellij.refactoring.util.DocCommentPolicy;
import com.intellij.refactoring.util.RefactoringUIUtil;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.refactoring.util.classMembers.MemberInfo;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewDescriptor;
import com.intellij.usageView.UsageViewUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Objects;

/**
 * @author dsl
 */
public abstract class ExtractSuperBaseProcessor extends TurnRefsToSuperProcessorBase {
  private static final Logger LOG = Logger.getInstance(ExtractSuperClassProcessor.class);
  protected PsiDirectory myTargetDirectory;
  protected final String myNewClassName;
  protected final MemberInfo[] myMemberInfos;
  protected final DocCommentPolicy myJavaDocPolicy;


  public ExtractSuperBaseProcessor(Project project,
                                   boolean replaceInstanceOf,
                                   PsiDirectory targetDirectory,
                                   String newClassName,
                                   PsiClass aClass, MemberInfo[] memberInfos, DocCommentPolicy javaDocPolicy) {
    super(project, replaceInstanceOf, newClassName);
    myTargetDirectory = targetDirectory;
    myNewClassName = newClassName;
    myClass = aClass;
    myMemberInfos = memberInfos;
    myJavaDocPolicy = javaDocPolicy;
  }

  @Override
  @NotNull
  protected UsageViewDescriptor createUsageViewDescriptor(UsageInfo @NotNull [] usages) {
    return new ExtractSuperClassViewDescriptor(myTargetDirectory, myClass, myMemberInfos);
  }

  protected boolean doesAnyExtractedInterfaceExtends(PsiClass aClass) {
    for (final MemberInfo memberInfo : myMemberInfos) {
      final PsiElement member = memberInfo.getMember();
      if (member instanceof PsiClass && memberInfo.getOverrides() != null) {
        if (InheritanceUtil.isInheritorOrSelf((PsiClass)member, aClass, true)) {
          return true;
        }
      }
    }
    return false;
  }

  protected boolean doMemberInfosContain(PsiMethod method) {
    for (final MemberInfo info : myMemberInfos) {
      if (info.getMember() instanceof PsiMethod) {
        if (MethodSignatureUtil.areSignaturesEqual(method, (PsiMethod)info.getMember())) return true;
      }
      else if (info.getMember() instanceof PsiClass && info.getOverrides() != null) {
        final PsiMethod methodBySignature = ((PsiClass)info.getMember()).findMethodBySignature(method, true);
        if (methodBySignature != null) {
          return true;
        }
      }
    }
    return false;
  }

  protected boolean doMemberInfosContain(final PsiField field) {
    for (final MemberInfo info : myMemberInfos) {
      if (myManager.areElementsEquivalent(field, info.getMember())) return true;
    }
    return false;
  }

  @Override
  protected UsageInfo @NotNull [] findUsages() {
    PsiReference[] refs = ReferencesSearch.search(myClass, GlobalSearchScope.projectScope(myProject), false).toArray(
      PsiReference.EMPTY_ARRAY);
    final ArrayList<UsageInfo> result = new ArrayList<>();
    detectTurnToSuperRefs(refs, result);
    final PsiPackage originalPackage = JavaDirectoryService.getInstance().getPackage(myClass.getContainingFile().getContainingDirectory());
    if (Comparing.equal(JavaDirectoryService.getInstance().getPackage(myTargetDirectory), originalPackage)) {
      result.clear();
    }
    for (final PsiReference ref : refs) {
      final PsiElement element = ref.getElement();
      if (!canTurnToSuper(element) && !RefactoringUtil.inImportStatement(ref, element)) {
        result.add(new BindToOldUsageInfo(element, ref, myClass));
      }
    }
    UsageInfo[] usageInfos = result.toArray(UsageInfo.EMPTY_ARRAY);
    return UsageViewUtil.removeDuplicatedUsages(usageInfos);
  }

  @Override
  protected void performRefactoring(UsageInfo @NotNull [] usages) {
    try {
      final String superClassName = myClass.getName();
      final String oldQualifiedName = myClass.getQualifiedName();
      myClass.setName(myNewClassName);
      PsiClass superClass = extractSuper(superClassName);
      final PsiDirectory initialDirectory = myClass.getContainingFile().getContainingDirectory();
      PsiFile containingFile = myClass.getContainingFile();
      try {
        if (myTargetDirectory != initialDirectory) {
          containingFile = (PsiFile)myTargetDirectory.add(myClass.getContainingFile().copy());
          myClass.getContainingFile().delete();
        }
      }
      catch (IncorrectOperationException e) {
        RefactoringUIUtil.processIncorrectOperation(myProject, e);
      }
      for (final UsageInfo usage : usages) {
        if (usage instanceof BindToOldUsageInfo) {
          final PsiReference reference = usage.getReference();
          if (reference != null && reference.getElement().isValid()) {
            reference.bindToElement(myClass);
          }
        }
      }
      if (!Objects.equals(oldQualifiedName, superClass.getQualifiedName())) {
        processTurnToSuperRefs(usages, superClass);
      }
      if (containingFile instanceof PsiJavaFile) {
        JavaCodeStyleManager.getInstance(myProject).removeRedundantImports((PsiJavaFile) containingFile);
      }
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }

    performVariablesRenaming();
  }

  protected abstract PsiClass extractSuper(String superClassName) throws IncorrectOperationException;

  @Override
  protected void refreshElements(PsiElement @NotNull [] elements) {
    myClass = (PsiClass)elements[0];
    myTargetDirectory = (PsiDirectory)elements[1];
    for (int i = 0; i < myMemberInfos.length; i++) {
      final MemberInfo info = myMemberInfos[i];
      info.updateMember((PsiMember)elements[i + 2]);
    }
  }

  @Override
  @NotNull
  protected String getCommandName() {
    return JavaRefactoringBundle.message("extract.subclass.command");
  }

  @NotNull
  @Override
  protected Collection<? extends PsiElement> getElementsToWrite(@NotNull final UsageViewDescriptor descriptor) {
    return ((ExtractSuperClassViewDescriptor) descriptor).getMembersToMakeWritable();
  }
}
