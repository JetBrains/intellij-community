/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.refactoring.extractSuperclass;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.MethodSignatureUtil;
import com.intellij.refactoring.RefactoringBundle;
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

/**
 * @author dsl
 */
public abstract class ExtractSuperBaseProcessor extends TurnRefsToSuperProcessorBase {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.extractSuperclass.ExtractSuperClassProcessor");
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

  @NotNull
  protected UsageViewDescriptor createUsageViewDescriptor(@NotNull UsageInfo[] usages) {
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

  @NotNull
  protected UsageInfo[] findUsages() {
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
    UsageInfo[] usageInfos = result.toArray(new UsageInfo[result.size()]);
    return UsageViewUtil.removeDuplicatedUsages(usageInfos);
  }

  protected void performRefactoring(@NotNull UsageInfo[] usages) {
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
      if (!Comparing.equal(oldQualifiedName, superClass.getQualifiedName())) {
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
  protected void refreshElements(@NotNull PsiElement[] elements) {
    myClass = (PsiClass)elements[0];
    myTargetDirectory = (PsiDirectory)elements[1];
    for (int i = 0; i < myMemberInfos.length; i++) {
      final MemberInfo info = myMemberInfos[i];
      info.updateMember((PsiMember)elements[i + 2]);
    }
  }

  @NotNull
  protected String getCommandName() {
    return RefactoringBundle.message("extract.subclass.command");
  }

  @NotNull
  @Override
  protected Collection<? extends PsiElement> getElementsToWrite(@NotNull final UsageViewDescriptor descriptor) {
    return ((ExtractSuperClassViewDescriptor) descriptor).getMembersToMakeWritable();
  }
}
