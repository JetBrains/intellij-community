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

package com.intellij.refactoring.makeStatic;

import com.intellij.lang.findUsages.DescriptiveNameUtil;
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
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.listeners.RefactoringEventData;
import com.intellij.refactoring.ui.ConflictsDialog;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.refactoring.util.ConflictsUtil;
import com.intellij.refactoring.util.RefactoringUIUtil;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewDescriptor;
import com.intellij.usageView.UsageViewUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public abstract class MakeMethodOrClassStaticProcessor<T extends PsiTypeParameterListOwner> extends BaseRefactoringProcessor {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.makeMethodStatic.MakeMethodStaticProcessor");

  protected T myMember;
  protected Settings mySettings;

  public MakeMethodOrClassStaticProcessor(Project project,
                                          T member,
                                          Settings settings) {
    super(project);
    myMember = member;
    mySettings = settings;
  }

  @NotNull
  protected UsageViewDescriptor createUsageViewDescriptor(@NotNull UsageInfo[] usages) {
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
  protected RefactoringEventData getAfterData(@NotNull UsageInfo[] usages) {
    RefactoringEventData data = new RefactoringEventData();
    data.addElement(myMember);
    return data;
  }

  protected final boolean preprocessUsages(@NotNull final Ref<UsageInfo[]> refUsages) {
    UsageInfo[] usagesIn = refUsages.get();
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

  private static UsageInfo[] filterOverriding(UsageInfo[] usages, Set<UsageInfo> suggestToMakeStatic) {
    ArrayList<UsageInfo> result = new ArrayList<>();
    for (UsageInfo usage : usages) {
      if (usage instanceof ChainedCallUsageInfo) {
        suggestToMakeStatic.add(usage);
      } else if (!(usage instanceof OverridingMethodUsageInfo)) {
        result.add(usage);
      }
    }
    return result.toArray(new UsageInfo[result.size()]);
  }

  private static UsageInfo[] filterInternalUsages(UsageInfo[] usages) {
    ArrayList<UsageInfo> result = new ArrayList<>();
    for (UsageInfo usage : usages) {
      if (!(usage instanceof InternalUsageInfo)) {
        result.add(usage);
      }
    }
    return result.toArray(new UsageInfo[result.size()]);
  }

  protected MultiMap<PsiElement,String> getConflictDescriptions(UsageInfo[] usages) {
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
              String message = RefactoringBundle.message("0.uses.non.static.1.which.is.not.passed.as.a.parameter", typeString,
                                                         RefactoringUIUtil.getDescription(field, true));
              conflicts.putValue(field, message);
            }
          }
          else {
            String message = RefactoringBundle.message("0.uses.1.which.needs.class.instance", typeString, RefactoringUIUtil.getDescription(referencedElement, true));
            conflicts.putValue(referencedElement, message);
          }
        }
      }
      if (usageInfo instanceof OverridingMethodUsageInfo) {
        LOG.assertTrue(myMember instanceof PsiMethod);
        final PsiMethod overridingMethod = ((PsiMethod)usageInfo.getElement());
        String message = RefactoringBundle.message("method.0.is.overridden.by.1", RefactoringUIUtil.getDescription(myMember, false),
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
    if (inaccessible.size() == 1) {
      final PsiField field = inaccessible.get(0);
      conflicts.putValue(field, RefactoringBundle.message("field.0.is.not.accessible",
                                       CommonRefactoringUtil.htmlEmphasize(field.getName()),
                                       RefactoringUIUtil.getDescription(container, true)));
    } else {

      for (int j = 0; j < inaccessible.size(); j++) {
        PsiField field = inaccessible.get(j);
        conflicts.putValue(field, RefactoringBundle.message("field.0.is.not.accessible",
                                       CommonRefactoringUtil.htmlEmphasize(field.getName()),
                                       RefactoringUIUtil.getDescription(container, true)));


      }
    }
  }

  @NotNull
  protected UsageInfo[] findUsages() {
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

    return result.toArray(new UsageInfo[result.size()]);
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
        result.add(new UsageInfo(element));
      }

      processExternalReference(element, method, result);
    }
  }

  protected void processExternalReference(PsiElement element, PsiMethod method, ArrayList<UsageInfo> result) {}

  //should be called before setting static modifier
  protected void setupTypeParameterList(T member) throws IncorrectOperationException {
    final PsiTypeParameterList list = member.getTypeParameterList();
    assert list != null;
    final PsiTypeParameterList newList = RefactoringUtil.createTypeParameterListWithUsedTypeParameters(member);
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

  @NotNull
  protected String getCommandName() {
    return RefactoringBundle.message("make.static.command", DescriptiveNameUtil.getDescriptiveName(myMember));
  }

  public T getMember() {
    return myMember;
  }

  public Settings getSettings() {
    return mySettings;
  }

  protected void performRefactoring(@NotNull UsageInfo[] usages) {
    PsiManager manager = myMember.getManager();
    PsiElementFactory factory = JavaPsiFacade.getInstance(manager.getProject()).getElementFactory();

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
      LOG.assertTrue(false);
    }
  }

  protected abstract void changeSelf(PsiElementFactory factory, UsageInfo[] usages) throws IncorrectOperationException;

  protected abstract void changeSelfUsage(SelfUsageInfo usageInfo) throws IncorrectOperationException;

  protected abstract void changeInternalUsage(InternalUsageInfo usage, PsiElementFactory factory) throws IncorrectOperationException;

  protected abstract void changeExternalUsage(UsageInfo usage, PsiElementFactory factory) throws IncorrectOperationException;
}
