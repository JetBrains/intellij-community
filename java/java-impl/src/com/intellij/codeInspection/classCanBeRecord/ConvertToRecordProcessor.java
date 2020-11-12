// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.classCanBeRecord;

import com.intellij.codeInspection.RedundantRecordConstructorInspection;
import com.intellij.codeInspection.RedundantRecordConstructorInspection.ConstructorSimplifier;
import com.intellij.codeInspection.classCanBeRecord.ConvertToRecordFix.FieldAccessorCandidate;
import com.intellij.codeInspection.classCanBeRecord.ConvertToRecordFix.RecordCandidate;
import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.rename.RenameProcessor;
import com.intellij.refactoring.rename.RenamePsiElementProcessor;
import com.intellij.refactoring.rename.RenameUtil;
import com.intellij.refactoring.ui.UsageViewDescriptorAdapter;
import com.intellij.refactoring.util.RefactoringConflictsUtil;
import com.intellij.refactoring.util.RefactoringUIUtil;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewDescriptor;
import com.intellij.util.ArrayUtil;
import com.intellij.util.SmartList;
import com.intellij.util.VisibilityUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.containers.SmartHashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.intellij.psi.PsiModifier.PRIVATE;

public class ConvertToRecordProcessor extends BaseRefactoringProcessor {
  private final RecordCandidate myRecordCandidate;
  private final boolean myShowWeakenVisibilityMembers;

  private final Map<PsiElement, String> myAllRenames = new LinkedHashMap<>();

  public ConvertToRecordProcessor(@NotNull RecordCandidate recordCandidate, boolean showWeakenVisibilityMembers) {
    super(recordCandidate.getProject());
    myRecordCandidate = recordCandidate;
    myShowWeakenVisibilityMembers = showWeakenVisibilityMembers;
  }

  @Override
  protected @NotNull UsageViewDescriptor createUsageViewDescriptor(UsageInfo @NotNull [] usages) {
    return new UsageViewDescriptorAdapter() {
      @Override
      public PsiElement @NotNull [] getElements() {
        return new PsiElement[]{myRecordCandidate.getPsiClass()};
      }

      @Override
      public String getProcessedElementsHeader() {
        return JavaRefactoringBundle.message("convert.to.record.title");
      }
    };
  }

  @Override
  protected UsageInfo @NotNull [] findUsages() {
    List<UsageInfo> usages = new SmartList<>();
    for (var entry : myRecordCandidate.getFieldAccessors().entrySet()) {
      PsiField psiField = entry.getKey();
      if (!psiField.hasModifierProperty(PRIVATE)) {
        usages.add(new FieldUsageInfo(psiField));
      }
      FieldAccessorCandidate fieldAccessorCandidate = entry.getValue();
      if (fieldAccessorCandidate != null && !fieldAccessorCandidate.isRecordStyleNaming()) {
        PsiMethod[] superMethods = fieldAccessorCandidate.getAccessor().findSuperMethods();
        String backingFieldName = fieldAccessorCandidate.getBackingField().getName();
        List<PsiMethod> methods;
        if (superMethods.length == 0) {
          methods = Collections.singletonList(fieldAccessorCandidate.getAccessor());
        }
        else {
          methods = List.of(superMethods);
        }
        RenamePsiElementProcessor methodRenameProcessor = RenamePsiElementProcessor.forElement(methods.get(0));
        methods.forEach(method -> {
          myAllRenames.put(method, backingFieldName);
          methodRenameProcessor.prepareRenaming(method, backingFieldName, myAllRenames);
          UsageInfo[] methodUsages = RenameUtil.findUsages(method, backingFieldName, false, false, myAllRenames);
          usages.addAll(Arrays.asList(methodUsages));
        });
        usages.add(new RenameMethodUsageInfo(fieldAccessorCandidate.getAccessor(), backingFieldName));
      }
    }

    if (myShowWeakenVisibilityMembers) {
      usages.addAll(findWeakenVisibilityUsages(myRecordCandidate));
    }
    return usages.toArray(UsageInfo.EMPTY_ARRAY);
  }

  @NotNull
  static List<UsageInfo> findWeakenVisibilityUsages(@NotNull RecordCandidate recordCandidate) {
    List<UsageInfo> result = new SmartList<>();
    for (var entry : recordCandidate.getFieldAccessors().entrySet()) {
      PsiField psiField = entry.getKey();
      FieldAccessorCandidate fieldAccessorCandidate = entry.getValue();
      if (fieldAccessorCandidate == null) {
        if (firstHasWeakerAccess(recordCandidate.getPsiClass(), psiField)) {
          result.add(new BrokenEncapsulationUsageInfo(psiField, JavaRefactoringBundle
            .message("convert.to.record.weakens.field.visibility", RefactoringUIUtil.getDescription(psiField, true),
                     VisibilityUtil.getVisibilityStringToDisplay(psiField), VisibilityUtil.toPresentableText(PsiModifier.PUBLIC))));
        }
      }
      else {
        PsiMethod accessor = fieldAccessorCandidate.getAccessor();
        if (firstHasWeakerAccess(recordCandidate.getPsiClass(), accessor)) {
          result.add(new BrokenEncapsulationUsageInfo(accessor, JavaRefactoringBundle
            .message("convert.to.record.weakens.accessor.visibility", RefactoringUIUtil.getDescription(accessor, true),
                     VisibilityUtil.getVisibilityStringToDisplay(accessor),
                     VisibilityUtil.toPresentableText(PsiModifier.PUBLIC))));
        }
      }
    }
    PsiMethod canonicalCtor = recordCandidate.getCanonicalConstructor();
    if (canonicalCtor != null && firstHasWeakerAccess(recordCandidate.getPsiClass(), canonicalCtor)) {
      result.add(new BrokenEncapsulationUsageInfo(canonicalCtor, JavaRefactoringBundle
        .message("convert.to.record.weakens.ctor.visibility", RefactoringUIUtil.getDescription(canonicalCtor, true),
                 VisibilityUtil.getVisibilityStringToDisplay(canonicalCtor),
                 VisibilityUtil.getVisibilityStringToDisplay(recordCandidate.getPsiClass()))));
    }
    return result;
  }

  @Override
  protected boolean preprocessUsages(@NotNull Ref<UsageInfo[]> refUsages) {
    final UsageInfo[] usages = refUsages.get();
    MultiMap<PsiElement, String> conflicts = new MultiMap<>();
    RenameUtil.addConflictDescriptions(usages, conflicts);
    Set<PsiField> conflictingFields = new SmartHashSet<>();
    for (UsageInfo usage : usages) {
      if (usage instanceof BrokenEncapsulationUsageInfo) {
        conflicts.putValue(usage.getElement(), ((BrokenEncapsulationUsageInfo)usage).myErrMsg);
      }
      else if (usage instanceof FieldUsageInfo) {
        conflictingFields.add(((FieldUsageInfo)usage).myField);
      }
      else if (usage instanceof RenameMethodUsageInfo) {
        RenameMethodUsageInfo renameMethodInfo = (RenameMethodUsageInfo)usage;
        RenamePsiElementProcessor renameMethodProcessor = RenamePsiElementProcessor.forElement(renameMethodInfo.myMethod);
        renameMethodProcessor.findExistingNameConflicts(renameMethodInfo.myMethod, renameMethodInfo.myNewName, conflicts, myAllRenames);
      }
    }
    RefactoringConflictsUtil.analyzeAccessibilityConflicts(conflictingFields, myRecordCandidate.getPsiClass(), conflicts, PRIVATE);

    if (!conflicts.isEmpty() && ApplicationManager.getApplication().isUnitTestMode()) {
      if (!ConflictsInTestsException.isTestIgnore()) {
        throw new ConflictsInTestsException(conflicts.values());
      }
      return true;
    }

    return showConflicts(conflicts, usages);
  }

  @Override
  protected void performRefactoring(UsageInfo @NotNull [] usages) {
    renameMembers(usages);

    PsiClass psiClass = myRecordCandidate.getPsiClass();
    PsiMethod canonicalCtor = myRecordCandidate.getCanonicalConstructor();
    Map<PsiField, FieldAccessorCandidate> fieldAccessors = myRecordCandidate.getFieldAccessors();
    RecordBuilder recordBuilder = new RecordBuilder(psiClass);
    PsiIdentifier classIdentifier = null;
    PsiElement nextElement = psiClass.getFirstChild();
    while (nextElement != null) {
      if (nextElement instanceof PsiKeyword && JavaTokenType.CLASS_KEYWORD.equals(((PsiKeyword)nextElement).getTokenType())) {
        recordBuilder.addRecordDeclaration();
      }
      else if (nextElement instanceof PsiIdentifier) {
        classIdentifier = (PsiIdentifier)nextElement;
        recordBuilder.addPsiElement(classIdentifier);
      }
      else if (nextElement instanceof PsiTypeParameterList) {
        recordBuilder.addPsiElement(nextElement);
        if (PsiTreeUtil.skipWhitespacesAndCommentsBackward(nextElement) == classIdentifier) {
          recordBuilder.addRecordHeader(canonicalCtor, fieldAccessors);
          classIdentifier = null;
        }
      }
      else if (nextElement instanceof PsiModifierList) {
        recordBuilder.addModifierList((PsiModifierList)nextElement);
      }
      else if (nextElement instanceof PsiField) {
        PsiField psiField = (PsiField)nextElement;
        psiField.normalizeDeclaration();
        if (fieldAccessors.containsKey(psiField)) {
          nextElement = PsiTreeUtil.skipWhitespacesForward(nextElement);
          continue;
        }
        recordBuilder.addPsiElement(nextElement);
      }
      else if (nextElement instanceof PsiMethod) {
        if (nextElement == canonicalCtor) {
          recordBuilder.addCanonicalCtor(canonicalCtor);
        }
        else {
          FieldAccessorCandidate fieldAccessorCandidate = getFieldAccessorCandidate(fieldAccessors, (PsiMethod)nextElement);
          if (fieldAccessorCandidate == null) {
            recordBuilder.addPsiElement(nextElement);
          }
          else {
            recordBuilder.addFieldAccessor(fieldAccessorCandidate);
          }
        }
      }
      else {
        recordBuilder.addPsiElement(nextElement);
      }
      nextElement = nextElement.getNextSibling();
    }

    PsiClass result = (PsiClass)psiClass.replace(recordBuilder.build());
    tryToCompactCanonicalCtor(result);
  }

  private void renameMembers(UsageInfo @NotNull [] usages) {
    List<UsageInfo> renameUsages = ContainerUtil.filter(usages, u -> !(u instanceof ConvertToRecordUsageInfo));
    MultiMap<PsiElement, UsageInfo> renameUsagesByElement = RenameProcessor.classifyUsages(myAllRenames.keySet(), renameUsages);
    for (var entry : myAllRenames.entrySet()) {
      PsiElement element = entry.getKey();
      String newName = entry.getValue();
      UsageInfo[] elementRenameUsages = renameUsagesByElement.get(entry.getKey()).toArray(UsageInfo.EMPTY_ARRAY);
      RenamePsiElementProcessor.forElement(element).renameElement(element, newName, elementRenameUsages, null);
    }
  }

  private static void tryToCompactCanonicalCtor(@NotNull PsiClass record) {
    PsiMethod canonicalCtor = ArrayUtil.getFirstElement(record.getConstructors());
    if (canonicalCtor != null) {
      ConstructorSimplifier ctorSimplifier = RedundantRecordConstructorInspection.createCtorSimplifier(canonicalCtor);
      if (ctorSimplifier != null) {
        ctorSimplifier.simplify(canonicalCtor);
      }
    }
  }

  @Override
  protected @NotNull @NlsContexts.Command String getCommandName() {
    return JavaRefactoringBundle.message("convert.to.record.title");
  }

  private static boolean firstHasWeakerAccess(@NotNull PsiModifierListOwner first, @NotNull PsiModifierListOwner second) {
    return VisibilityUtil.compare(VisibilityUtil.getVisibilityModifier(first.getModifierList()),
                                  VisibilityUtil.getVisibilityModifier(second.getModifierList())) < 0;
  }

  @Nullable
  private static FieldAccessorCandidate getFieldAccessorCandidate(@NotNull Map<PsiField, @Nullable FieldAccessorCandidate> fieldAccessors,
                                                                  @NotNull PsiMethod psiMethod) {
    return ContainerUtil.find(fieldAccessors.values(), value -> value != null && psiMethod.equals(value.getAccessor()));
  }
}
