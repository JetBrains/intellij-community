// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.refactoring.memberPullUp;

import com.intellij.analysis.AnalysisScope;
import com.intellij.java.JavaBundle;
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
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.Query;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class PullUpProcessor extends BaseRefactoringProcessor implements PullUpData {
  private static final Logger LOG = Logger.getInstance(PullUpProcessor.class);
  @NotNull
  private final PsiClass mySourceClass;
  private final PsiClass myTargetSuperClass;
  private final MemberInfo[] myMembersToMove;
  private final DocCommentPolicy myJavaDocPolicy;
  private Set<PsiMember> myMembersAfterMove;
  private Set<PsiMember> myMovedMembers;
  private final Map<Language, PullUpHelper<MemberInfo>> myProcessors = new HashMap<>();

  public PullUpProcessor(@NotNull PsiClass sourceClass, PsiClass targetSuperClass, MemberInfo[] membersToMove, DocCommentPolicy javaDocPolicy) {
    super(sourceClass.getProject());
    mySourceClass = sourceClass;
    myTargetSuperClass = targetSuperClass;
    myMembersToMove = membersToMove;
    myJavaDocPolicy = javaDocPolicy;
  }

  @Override
  @NotNull
  protected UsageViewDescriptor createUsageViewDescriptor(UsageInfo @NotNull [] usages) {
    return new PullUpUsageViewDescriptor();
  }

  @Override
  protected UsageInfo @NotNull [] findUsages() {
    final List<UsageInfo> result = new ArrayList<>();
    for (MemberInfo memberInfo : myMembersToMove) {
      final PsiMember member = memberInfo.getMember();
      if (member.hasModifierProperty(PsiModifier.STATIC)) {
        for (PsiReference reference : ReferencesSearch.search(member)) {
          result.add(new UsageInfo(reference));
        }
      }
    }
    return result.isEmpty() ? UsageInfo.EMPTY_ARRAY : result.toArray(UsageInfo.EMPTY_ARRAY);
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
    data.addMembers(myMembersToMove, info -> info.getMember());
    return data;
  }

  @Nullable
  @Override
  protected RefactoringEventData getAfterData(UsageInfo @NotNull [] usages) {
    final RefactoringEventData data = new RefactoringEventData();
    data.addElement(myTargetSuperClass);
    return data;
  }

  @Override
  protected void performRefactoring(UsageInfo @NotNull [] usages) {
    moveMembersToBase();
    moveFieldInitializations();
    for (UsageInfo usage : usages) {
      PsiElement element = usage.getElement();
      if (element == null) continue;

      PullUpHelper<MemberInfo> processor = getProcessor(element);
      if (processor == null) continue;

      processor.updateUsage(element);
    }
    ApplicationManager.getApplication().invokeLater(() -> processMethodsDuplicates(), ModalityState.NON_MODAL, myProject.getDisposed());
  }

  private void processMethodsDuplicates() {
    ProgressManager.getInstance().runProcessWithProgressSynchronously(() -> ApplicationManager.getApplication().runReadAction(() -> {
      if (!myTargetSuperClass.isValid()) return;
      final Query<PsiClass> search = ClassInheritorsSearch.search(myTargetSuperClass);
      final Set<VirtualFile> hierarchyFiles = new HashSet<>();
      for (PsiClass aClass : search) {
        final PsiFile containingFile = aClass.getContainingFile();
        if (containingFile != null) {
          final VirtualFile virtualFile = containingFile.getVirtualFile();
          if (virtualFile != null) {
            hierarchyFiles.add(virtualFile);
          }
        }
      }
      final Set<PsiMember> methodsToSearchDuplicates = new HashSet<>();
      for (PsiMember psiMember : myMembersAfterMove) {
        if (psiMember instanceof PsiMethod && psiMember.isValid() && ((PsiMethod)psiMember).getBody() != null) {
          methodsToSearchDuplicates.add(psiMember);
        }
      }

      MethodDuplicatesHandler.invokeOnScope(myProject, methodsToSearchDuplicates, new AnalysisScope(myProject, hierarchyFiles), true);
    }), MethodDuplicatesHandler.getRefactoringName(), true, myProject);
  }

  @NotNull
  @Override
  protected String getCommandName() {
    return RefactoringBundle.message("pullUp.command", DescriptiveNameUtil.getDescriptiveName(mySourceClass));
  }

  public void moveMembersToBase() throws IncorrectOperationException {
    myMovedMembers = new LinkedHashSet<>();
    myMembersAfterMove = new LinkedHashSet<>();

    // build aux sets
    for (MemberInfo info : myMembersToMove) {
      myMovedMembers.add(info.getMember());
    }

    final PsiSubstitutor substitutor = upDownSuperClassSubstitutor();

    for (MemberInfo info : myMembersToMove) {
      PullUpHelper<MemberInfo> processor = getProcessor(info);

      LOG.assertTrue(processor != null, info.getMember());
      if (!(info.getMember() instanceof PsiClass) || info.getOverrides() == null) {
        processor.setCorrectVisibility(info);
        processor.encodeContextInfo(info);
      }

      processor.move(info, substitutor);
    }

    for (PsiMember member : myMembersAfterMove) {
      PullUpHelper<MemberInfo> processor = getProcessor(member);
      LOG.assertTrue(processor != null, member);

      processor.postProcessMember(member);

      final JavaRefactoringListenerManager listenerManager = JavaRefactoringListenerManager.getInstance(myProject);
      ((JavaRefactoringListenerManagerImpl)listenerManager).fireMemberMoved(mySourceClass, member);
    }
  }

  @Nullable
  private PullUpHelper<MemberInfo> getProcessor(@NotNull PsiElement element) {
    Language language = element.getLanguage();
    return getProcessor(language);
  }

  @Nullable
  private PullUpHelper<MemberInfo> getProcessor(Language language) {
    PullUpHelper<MemberInfo> helper = myProcessors.get(language);
    if (helper == null) {
      PullUpHelperFactory helperFactory = PullUpHelper.INSTANCE.forLanguage(language);
      if (helperFactory == null) {
        return null;
      }
      helper = helperFactory.createPullUpHelper(this);
      myProcessors.put(language, helper);
    }
    return helper;
  }

  @Nullable
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

    final LinkedHashSet<PsiField> movedFields = new LinkedHashSet<>();
    for (PsiMember member : myMembersAfterMove) {
      if (member instanceof PsiField) {
        movedFields.add((PsiField)member);
      }
    }

    if (movedFields.isEmpty()) return;

    PullUpHelper<MemberInfo> processor = getProcessor(myTargetSuperClass);
    LOG.assertTrue(processor != null, myTargetSuperClass);
    processor.moveFieldInitializations(movedFields);
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

  @NotNull
  @Override
  protected Collection<? extends PsiElement> getElementsToWrite(@NotNull UsageViewDescriptor descriptor) {
    return Collections.singletonList(mySourceClass);
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
    @Override
    public String getProcessedElementsHeader() {
      return JavaBundle.message("pull.up.members.usage.view.description.processed.elements.node", DescriptiveNameUtil.getDescriptiveName(mySourceClass));
    }

    @Override
    public PsiElement @NotNull [] getElements() {
      return ContainerUtil.map(myMembersToMove, info -> info.getMember(), PsiElement.EMPTY_ARRAY);
    }

    @NotNull
    @Override
    public String getCodeReferencesText(int usagesCount, int filesCount) {
      return JavaBundle.message("pull.up.members.usage.view.description.code.references.node", RefactoringUIUtil.getDescription(myTargetSuperClass, true));
    }
  }
}
