/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

/*
 * Created by IntelliJ IDEA.
 * User: dsl
 * Date: 14.06.2002
 * Time: 22:35:19
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.refactoring.memberPullUp;

import com.intellij.analysis.AnalysisScope;
import com.intellij.lang.Language;
import com.intellij.lang.findUsages.DescriptiveNameUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.classMembers.MemberInfoBase;
import com.intellij.refactoring.listeners.JavaRefactoringListenerManager;
import com.intellij.refactoring.listeners.RefactoringEventData;
import com.intellij.refactoring.listeners.impl.JavaRefactoringListenerManagerImpl;
import com.intellij.refactoring.util.DocCommentPolicy;
import com.intellij.refactoring.util.RefactoringUIUtil;
import com.intellij.refactoring.util.classMembers.MemberInfo;
import com.intellij.refactoring.util.duplicates.MethodDuplicatesHandler;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewDescriptor;
import com.intellij.util.Function;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.Query;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class PullUpProcessor extends BaseRefactoringProcessor implements PullUpData {
  private static final Logger LOG = Logger.getInstance(PullUpProcessor.class);

  private final PsiClass mySourceClass;
  private final PsiClass myTargetSuperClass;
  private final MemberInfo[] myMembersToMove;
  private final DocCommentPolicy myJavaDocPolicy;
  private Set<PsiMember> myMembersAfterMove = null;
  private Set<PsiMember> myMovedMembers = null;
  private Map<Language, PullUpHelper<MemberInfo>> myProcessors = ContainerUtil.newHashMap();

  public PullUpProcessor(PsiClass sourceClass, PsiClass targetSuperClass, MemberInfo[] membersToMove, DocCommentPolicy javaDocPolicy) {
    super(sourceClass.getProject());
    mySourceClass = sourceClass;
    myTargetSuperClass = targetSuperClass;
    myMembersToMove = membersToMove;
    myJavaDocPolicy = javaDocPolicy;
  }

  @NotNull
  protected UsageViewDescriptor createUsageViewDescriptor(UsageInfo[] usages) {
    return new PullUpUsageViewDescriptor();
  }

  @NotNull
  protected UsageInfo[] findUsages() {
    final List<UsageInfo> result = new ArrayList<UsageInfo>();
    for (MemberInfo memberInfo : myMembersToMove) {
      final PsiMember member = memberInfo.getMember();
      if (member.hasModifierProperty(PsiModifier.STATIC)) {
        for (PsiReference reference : ReferencesSearch.search(member)) {
          result.add(new UsageInfo(reference));
        }
      }
    }
    return result.isEmpty() ? UsageInfo.EMPTY_ARRAY : result.toArray(new UsageInfo[result.size()]);
  }

  @Nullable
  @Override
  protected String getRefactoringId() {
    return "refactoring.pull.up";
  }

  @Nullable
  @Override
  protected RefactoringEventData getBeforeData() {
    RefactoringEventData data = new RefactoringEventData();
    data.addElement(mySourceClass);
    data.addMembers(myMembersToMove, new Function<MemberInfo, PsiElement>() {
      @Override
      public PsiElement fun(MemberInfo info) {
        return info.getMember();
      }
    });
    return data;
  }

  @Nullable
  @Override
  protected RefactoringEventData getAfterData(UsageInfo[] usages) {
    final RefactoringEventData data = new RefactoringEventData();
    data.addElement(myTargetSuperClass);
    return data;
  }

  protected void performRefactoring(UsageInfo[] usages) {
    moveMembersToBase();
    moveFieldInitializations();
    for (UsageInfo usage : usages) {
      PsiElement element = usage.getElement();
      if (element == null) continue;

      PullUpHelper<MemberInfo> processor = getProcessor(element);
      processor.updateUsage(element);
    }
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        processMethodsDuplicates();
      }
    }, ModalityState.NON_MODAL, myProject.getDisposed());
  }

  private void processMethodsDuplicates() {
    if (!myTargetSuperClass.isValid()) return;
    ProgressManager.getInstance().runProcessWithProgressSynchronously(new Runnable() {
      @Override
      public void run() {
        final Query<PsiClass> search = ClassInheritorsSearch.search(myTargetSuperClass);
        final Set<VirtualFile> hierarchyFiles = new HashSet<VirtualFile>();
        for (PsiClass aClass : search) {
          final PsiFile containingFile = aClass.getContainingFile();
          if (containingFile != null) {
            final VirtualFile virtualFile = containingFile.getVirtualFile();
            if (virtualFile != null) {
              hierarchyFiles.add(virtualFile);
            }
          }
        }
        final Set<PsiMember> methodsToSearchDuplicates = new HashSet<PsiMember>();
        for (PsiMember psiMember : myMembersAfterMove) {
          if (psiMember instanceof PsiMethod && psiMember.isValid() && ((PsiMethod)psiMember).getBody() != null) {
            methodsToSearchDuplicates.add(psiMember);
          }
        }

        MethodDuplicatesHandler.invokeOnScope(myProject, methodsToSearchDuplicates, new AnalysisScope(myProject, hierarchyFiles), true);
      }
    }, MethodDuplicatesHandler.REFACTORING_NAME, true, myProject);
  }

  protected String getCommandName() {
    return RefactoringBundle.message("pullUp.command", DescriptiveNameUtil.getDescriptiveName(mySourceClass));
  }

  public void moveMembersToBase() throws IncorrectOperationException {
    myMovedMembers = ContainerUtil.newHashSet();
    myMembersAfterMove = ContainerUtil.newHashSet();

    // build aux sets
    for (MemberInfo info : myMembersToMove) {
      myMovedMembers.add(info.getMember());
    }

    final PsiSubstitutor substitutor = upDownSuperClassSubstitutor();

    for (MemberInfo info : myMembersToMove) {
      PullUpHelper<MemberInfo> processor = getProcessor(info);

      if (!(info.getMember() instanceof PsiClass) || info.getOverrides() == null) {
        processor.setCorrectVisibility(info);
        processor.encodeContextInfo(info);
      }

      processor.move(info, substitutor);
    }

    for (PsiMember member : myMembersAfterMove) {
      getProcessor(member).postProcessMember(member);

      final JavaRefactoringListenerManager listenerManager = JavaRefactoringListenerManager.getInstance(myProject);
      ((JavaRefactoringListenerManagerImpl)listenerManager).fireMemberMoved(mySourceClass, member);
    }
  }

  private PullUpHelper<MemberInfo> getProcessor(@NotNull PsiElement element) {
    Language language = element.getLanguage();
    return getProcessor(language);
  }

  private PullUpHelper<MemberInfo> getProcessor(Language language) {
    PullUpHelper<MemberInfo> helper = myProcessors.get(language);
    if (helper == null) {
      helper = PullUpHelper.INSTANCE.forLanguage(language).createPullUpHelper(this);
      myProcessors.put(language, helper);
    }
    return helper;
  }

  private PullUpHelper<MemberInfo> getProcessor(@NotNull MemberInfo info) {
    PsiReferenceList refList = info.getSourceReferenceList();
    if (refList != null) {
      return getProcessor(refList.getLanguage());
    }
    return getProcessor(info.getMember());
  }

  private PsiSubstitutor upDownSuperClassSubstitutor() {
    PsiSubstitutor substitutor = PsiSubstitutor.EMPTY;
    for (PsiTypeParameter parameter : PsiUtil.typeParametersIterable(mySourceClass)) {
      substitutor = substitutor.put(parameter, null);
    }
    final Map<PsiTypeParameter, PsiType> substitutionMap =
      TypeConversionUtil.getSuperClassSubstitutor(myTargetSuperClass, mySourceClass, PsiSubstitutor.EMPTY).getSubstitutionMap();
    for (PsiTypeParameter parameter : substitutionMap.keySet()) {
      final PsiType type = substitutionMap.get(parameter);
      final PsiClass resolvedClass = PsiUtil.resolveClassInType(type);
      if (resolvedClass instanceof PsiTypeParameter) {
        substitutor = substitutor.put((PsiTypeParameter)resolvedClass, JavaPsiFacade.getElementFactory(myProject).createType(parameter));
      }
    }
    return substitutor;
  }

  public void moveFieldInitializations() throws IncorrectOperationException {
    LOG.assertTrue(myMembersAfterMove != null);

    final LinkedHashSet<PsiField> movedFields = new LinkedHashSet<PsiField>();
    for (PsiMember member : myMembersAfterMove) {
      if (member instanceof PsiField) {
        movedFields.add((PsiField)member);
      }
    }

    if (movedFields.isEmpty()) return;

    getProcessor(myTargetSuperClass).moveFieldInitializations(movedFields);
  }

  public static boolean checkedInterfacesContain(Collection<? extends MemberInfoBase<? extends PsiMember>> memberInfos, PsiMethod psiMethod) {
    for (MemberInfoBase<? extends PsiMember> memberInfo : memberInfos) {
      if (memberInfo.isChecked() &&
          memberInfo.getMember() instanceof PsiClass &&
          Boolean.FALSE.equals(memberInfo.getOverrides())) {
        if (((PsiClass)memberInfo.getMember()).findMethodBySignature(psiMethod, true) != null) {
          return true;
        }
      }
    }
    return false;
  }

  @Override
  public PsiClass getSourceClass() {
    return mySourceClass;
  }

  @Override
  public PsiClass getTargetClass() {
    return myTargetSuperClass;
  }

  @Override
  public DocCommentPolicy getDocCommentPolicy() {
    return myJavaDocPolicy;
  }

  @Override
  public Set<PsiMember> getMembersToMove() {
    return myMovedMembers;
  }

  @Override
  public Set<PsiMember> getMovedMembers() {
    return myMembersAfterMove;
  }

  @Override
  public Project getProject() {
    return myProject;
  }

  private class PullUpUsageViewDescriptor implements UsageViewDescriptor {
    public String getProcessedElementsHeader() {
      return "Pull up members from";
    }

    @NotNull
    public PsiElement[] getElements() {
      return new PsiElement[]{mySourceClass};
    }

    public String getCodeReferencesText(int usagesCount, int filesCount) {
      return "Class to pull up members to \"" + RefactoringUIUtil.getDescription(myTargetSuperClass, true) + "\"";
    }

    public String getCommentReferencesText(int usagesCount, int filesCount) {
      return null;
    }
  }
}
