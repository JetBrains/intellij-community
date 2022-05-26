// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.refactoring.makeStatic;

import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.lang.findUsages.DescriptiveNameUtil;
import com.intellij.model.PsiElementUsageInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.search.searches.OverridingMethodsSearch;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.listeners.RefactoringEventData;
import com.intellij.refactoring.ui.ConflictsDialog;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.refactoring.util.ConflictsUtil;
import com.intellij.refactoring.util.RefactoringUIUtil;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewDescriptor;
import com.intellij.usageView.UsageViewUtil;
import com.intellij.util.CommonJavaRefactoringUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public abstract class MakeMethodOrClassStaticProcessor<T extends PsiTypeParameterListOwner> extends BaseRefactoringProcessor {
  private static final Logger LOG = Logger.getInstance(MakeMethodStaticProcessor.class);

  protected T myMember;
  protected Settings mySettings;

  public MakeMethodOrClassStaticProcessor(Project project,
                                          T member,
                                          Settings settings) {
    super(project);
    myMember = member;
    mySettings = settings;
  }

  @Override
  @NotNull
  protected UsageViewDescriptor createUsageViewDescriptor(UsageInfo @NotNull [] usages) {
    return new MakeMethodOrClassStaticViewDescriptor(myMember);
  }

  @Nullable
  @Override
  protected String getRefactoringId() {
    return "refactoring.makeStatic";
  }

  @Nullable
  @Override
  protected RefactoringEventData getBeforeData() {
    RefactoringEventData data = new RefactoringEventData();
    data.addElement(myMember);
    return data;
  }

  @Nullable
  @Override
  protected RefactoringEventData getAfterData(UsageInfo @NotNull [] usages) {
    RefactoringEventData data = new RefactoringEventData();
    data.addElement(myMember);
    return data;
  }

  @Override
  protected final boolean preprocessUsages(@NotNull final Ref<UsageInfo[]> refUsages) {
    UsageInfo[] usagesIn = refUsages.get();
    if (ApplicationManager.getApplication().isUnitTestMode() && !BaseRefactoringProcessor.ConflictsInTestsException.isTestIgnore()) {
      MultiMap<PsiElement, @Nls String> conflictDescriptions = getConflictDescriptions(usagesIn);
      if (!conflictDescriptions.isEmpty()) {
        throw new ConflictsInTestsException(conflictDescriptions.values());
      }
    }
    if (myPrepareSuccessfulSwingThreadCallback != null) {
      MultiMap<PsiElement, String> conflicts = getConflictDescriptions(usagesIn);
      if (conflicts.size() > 0) {
        ConflictsDialog conflictsDialog = prepareConflictsDialog(conflicts, refUsages.get());
        if (!conflictsDialog.showAndGet()) {
          if (conflictsDialog.isShowConflicts()) prepareSuccessful();
          return false;
        }
      }
      if(!mySettings.isChangeSignature()) {
        refUsages.set(filterInternalUsages(usagesIn));
      }
    }
    final Set<UsageInfo> toMakeStatic = new LinkedHashSet<>();
    refUsages.set(filterOverriding(usagesIn, toMakeStatic));
    if (!findAdditionalMembers(toMakeStatic)) return false;
    prepareSuccessful();
    return true;
  }

  protected boolean findAdditionalMembers(Set<UsageInfo> toMakeStatic) {return true;}

  private static UsageInfo[] filterOverriding(UsageInfo[] usages, Set<? super UsageInfo> suggestToMakeStatic) {
    ArrayList<UsageInfo> result = new ArrayList<>();
    for (UsageInfo usage : usages) {
      if (usage instanceof ChainedCallUsageInfo) {
        suggestToMakeStatic.add(usage);
      } else if (!(usage instanceof OverridingMethodUsageInfo)) {
        result.add(usage);
      }
    }
    return result.toArray(UsageInfo.EMPTY_ARRAY);
  }

  private static UsageInfo[] filterInternalUsages(UsageInfo[] usages) {
    ArrayList<UsageInfo> result = new ArrayList<>();
    for (UsageInfo usage : usages) {
      if (!(usage instanceof InternalUsageInfo)) {
        result.add(usage);
      }
    }
    return result.toArray(UsageInfo.EMPTY_ARRAY);
  }

  protected MultiMap<PsiElement, @Nls String> getConflictDescriptions(UsageInfo[] usages) {
    MultiMap<PsiElement, String> conflicts = new MultiMap<>();
    HashSet<PsiElement> processed = new HashSet<>();
    String typeString = StringUtil.capitalize(UsageViewUtil.getType(myMember));
    for (UsageInfo usageInfo : usages) {
      if (usageInfo instanceof InternalUsageInfo && !(usageInfo instanceof SelfUsageInfo)) {
        PsiElement referencedElement = ((InternalUsageInfo)usageInfo).getReferencedElement();
        if (!mySettings.isMakeClassParameter()) {
          if (referencedElement instanceof PsiModifierListOwner) {
            if (((PsiModifierListOwner)referencedElement).hasModifierProperty(PsiModifier.STATIC)) {
              continue;
            }
          }

          if (processed.contains(referencedElement)) continue;
          processed.add(referencedElement);
          if (referencedElement instanceof PsiField) {
            PsiField field = (PsiField)referencedElement;

            if (mySettings.getNameForField(field) == null) {
              String message = JavaRefactoringBundle.message("0.uses.non.static.1.which.is.not.passed.as.a.parameter", typeString,
                                                         RefactoringUIUtil.getDescription(field, true));
              conflicts.putValue(field, message);
            }
          }
          else {
            String message = JavaRefactoringBundle.message("0.uses.1.which.needs.class.instance", typeString, RefactoringUIUtil.getDescription(referencedElement, true));
            conflicts.putValue(referencedElement, message);
          }
        }
      }
      if (usageInfo instanceof OverridingMethodUsageInfo) {
        LOG.assertTrue(myMember instanceof PsiMethod);
        final PsiMethod overridingMethod = ((PsiMethod)usageInfo.getElement());
        String message = JavaRefactoringBundle.message("method.0.is.overridden.by.1", RefactoringUIUtil.getDescription(myMember, false),
                                                   RefactoringUIUtil.getDescription(overridingMethod, true));
        conflicts.putValue(overridingMethod, message);
      }
      else {
        PsiElement element = usageInfo.getElement();
        PsiElement container = ConflictsUtil.getContainer(element);
        if (processed.contains(container)) continue;
        processed.add(container);
        List<Settings.FieldParameter> fieldParameters = mySettings.getParameterOrderList();
        ArrayList<PsiField> inaccessible = new ArrayList<>();

        for (final Settings.FieldParameter fieldParameter : fieldParameters) {
          if (!PsiUtil.isAccessible(fieldParameter.field, element, null)) {
            inaccessible.add(fieldParameter.field);
          }
        }

        if (inaccessible.isEmpty()) continue;

        createInaccessibleFieldsConflictDescription(inaccessible, container, conflicts);
      }
    }
    return conflicts;
  }

  private static void createInaccessibleFieldsConflictDescription(ArrayList<PsiField> inaccessible, PsiElement container,
                                                                                     MultiMap<PsiElement, String> conflicts) {
    for (PsiField field : inaccessible) {
      conflicts.putValue(field, JavaRefactoringBundle.message(
        "field.0.is.not.accessible", CommonRefactoringUtil.htmlEmphasize(field.getName()), 
        RefactoringUIUtil.getDescription(container, true)));
    }
  }

  @Override
  protected UsageInfo @NotNull [] findUsages() {
    ArrayList<UsageInfo> result = new ArrayList<>();

    ContainerUtil.addAll(result, MakeStaticUtil.findClassRefsInMember(myMember, true));

    if (mySettings.isReplaceUsages()) {
      findExternalUsages(result);
    }

    if (myMember instanceof PsiMethod) {
      final PsiMethod[] overridingMethods =
        OverridingMethodsSearch.search((PsiMethod)myMember, myMember.getUseScope(), false).toArray(PsiMethod.EMPTY_ARRAY);
      for (PsiMethod overridingMethod : overridingMethods) {
        if (overridingMethod != myMember) {
          result.add(new OverridingMethodUsageInfo(overridingMethod));
        }
      }
    }

    return result.toArray(UsageInfo.EMPTY_ARRAY);
  }

  protected abstract void findExternalUsages(ArrayList<UsageInfo> result);

  protected void findExternalReferences(final PsiMethod method, final ArrayList<UsageInfo> result) {
    for (PsiReference ref : ReferencesSearch.search(method)) {
      PsiElement element = ref.getElement();
      PsiElement qualifier = null;
      if (element instanceof PsiReferenceExpression) {
        qualifier = ((PsiReferenceExpression)element).getQualifierExpression();
        if (qualifier instanceof PsiThisExpression) qualifier = null;
      }
      if (!PsiTreeUtil.isAncestor(myMember, element, true) || qualifier != null) {
        result.add(new PsiElementUsageInfo(element));
      }

      processExternalReference(element, method, result);
    }
  }

  protected void processExternalReference(PsiElement element, PsiMethod method, ArrayList<UsageInfo> result) {}

  //should be called before setting static modifier
  protected void setupTypeParameterList(T member) throws IncorrectOperationException {
    final PsiTypeParameterList list = member.getTypeParameterList();
    assert list != null;
    final PsiTypeParameterList newList = CommonJavaRefactoringUtil.createTypeParameterListWithUsedTypeParameters(member);
    if (newList != null) {
      list.replace(newList);
    }
  }

  protected boolean makeClassParameterFinal(UsageInfo[] usages) {
    for (UsageInfo usage : usages) {
      if (usage instanceof InternalUsageInfo) {
        final InternalUsageInfo internalUsageInfo = (InternalUsageInfo)usage;
        PsiElement referencedElement = internalUsageInfo.getReferencedElement();
        if (!(referencedElement instanceof PsiField)
            || mySettings.getNameForField((PsiField)referencedElement) == null) {
          if (internalUsageInfo.isInsideAnonymous()) {
            return true;
          }
        }
      }
    }
    return false;
  }

  protected static boolean makeFieldParameterFinal(PsiField field, UsageInfo[] usages) {
    for (UsageInfo usage : usages) {
      if (usage instanceof InternalUsageInfo) {
        final InternalUsageInfo internalUsageInfo = (InternalUsageInfo)usage;
        PsiElement referencedElement = internalUsageInfo.getReferencedElement();
        if (referencedElement instanceof PsiField && field.equals(referencedElement)) {
          if (internalUsageInfo.isInsideAnonymous()) {
            return true;
          }
        }
      }
    }
    return false;
  }

  @Override
  @NotNull
  protected String getCommandName() {
    return JavaRefactoringBundle.message("make.static.command", DescriptiveNameUtil.getDescriptiveName(myMember));
  }

  public T getMember() {
    return myMember;
  }

  public Settings getSettings() {
    return mySettings;
  }

  @Override
  protected void performRefactoring(UsageInfo @NotNull [] usages) {
    PsiManager manager = myMember.getManager();
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(manager.getProject());

    try {
      for (UsageInfo usage : usages) {
        if (usage instanceof SelfUsageInfo) {
          changeSelfUsage((SelfUsageInfo)usage);
        }
        else if (usage instanceof InternalUsageInfo) {
          changeInternalUsage((InternalUsageInfo)usage, factory);
        }
        else {
          changeExternalUsage(usage, factory);
        }
      }
      changeSelf(factory, usages);
    }
    catch (IncorrectOperationException ex) {
      LOG.error(ex);
    }
  }

  protected abstract void changeSelf(PsiElementFactory factory, UsageInfo[] usages) throws IncorrectOperationException;

  protected abstract void changeSelfUsage(SelfUsageInfo usageInfo) throws IncorrectOperationException;

  protected abstract void changeInternalUsage(InternalUsageInfo usage, PsiElementFactory factory) throws IncorrectOperationException;

  protected abstract void changeExternalUsage(UsageInfo usage, PsiElementFactory factory) throws IncorrectOperationException;
}
