// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.changeClassSignature;

import com.intellij.history.LocalHistory;
import com.intellij.history.LocalHistoryAction;
import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.changeSignature.ChangeSignatureUtil;
import com.intellij.refactoring.listeners.RefactoringEventData;
import com.intellij.refactoring.util.RefactoringUIUtil;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewDescriptor;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class ChangeClassSignatureProcessor extends BaseRefactoringProcessor {
  private static final Logger LOG = Logger.getInstance(ChangeClassSignatureProcessor.class);
  private PsiClass myClass;
  private final TypeParameterInfo[] myNewSignature;

  public ChangeClassSignatureProcessor(Project project, PsiClass aClass, TypeParameterInfo[] newSignature) {
    super(project);
    myClass = aClass;
    myNewSignature = newSignature;
  }

  @Override
  protected void refreshElements(PsiElement @NotNull [] elements) {
    LOG.assertTrue(elements.length == 1);
    LOG.assertTrue(elements[0] instanceof PsiClass);
    myClass = (PsiClass)elements[0];
  }

  @Override
  @NotNull
  protected String getCommandName() {
    return ChangeClassSignatureDialog.getRefactoringName();
  }

  @Override
  @NotNull
  protected UsageViewDescriptor createUsageViewDescriptor(UsageInfo @NotNull [] usages) {
    return new ChangeClassSigntaureViewDescriptor(myClass);
  }

  @Override
  protected boolean preprocessUsages(@NotNull Ref<UsageInfo[]> refUsages) {
    final MultiMap<PsiElement, String> conflicts = new MultiMap<>();

    final PsiTypeParameter[] parameters = myClass.getTypeParameters();
    final Map<String, TypeParameterInfo> infos = new HashMap<>();
    for (TypeParameterInfo info : myNewSignature) {
      final String newName = info.getName(parameters);
      TypeParameterInfo existing = infos.get(newName);
      if (existing != null) {
        String classDescription = RefactoringUIUtil.getDescription(myClass, false);
        String message = JavaRefactoringBundle.message("changeClassSignature.already.contains.type.parameter", classDescription, newName);
        conflicts.putValue(myClass, message);
      }
      infos.put(newName, info);
    }
    return showConflicts(conflicts, refUsages.get());
  }

  @Override
  protected UsageInfo @NotNull [] findUsages() {
    GlobalSearchScope projectScope = GlobalSearchScope.projectScope(myProject);
    List<UsageInfo> result = new ArrayList<>();

    boolean hadTypeParameters = myClass.hasTypeParameters();
    for (final PsiReference reference : ReferencesSearch.search(myClass, projectScope, false)) {
      if (reference.getElement() instanceof PsiJavaCodeReferenceElement referenceElement) {
        PsiElement parent = referenceElement.getParent();
        if (parent instanceof PsiTypeElement && (parent.getParent() instanceof PsiInstanceOfExpression ||
                                                 parent.getParent() instanceof PsiClassObjectAccessExpression)) {
          continue;
        }
        if (parent instanceof PsiNewExpression newExpression && PsiDiamondType.hasDiamond(newExpression)) {
          continue;
        }
        if (parent instanceof PsiTypeElement || parent instanceof PsiNewExpression || parent instanceof PsiAnonymousClass ||
            parent instanceof PsiReferenceList) {
          if (!hadTypeParameters || referenceElement.getTypeParameters().length > 0) {
            result.add(new UsageInfo(referenceElement));
          }
        }
      }
    }
    return result.toArray(UsageInfo.EMPTY_ARRAY);
  }

  @Override
  protected void performRefactoring(UsageInfo @NotNull [] usages) {
    LocalHistoryAction a = LocalHistory.getInstance().startAction(getCommandName());
    try {
      doRefactoring(usages);
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
    finally {
      a.finish();
    }
  }

  @Nullable
  @Override
  protected String getRefactoringId() {
    return "refactoring.changeClassSignature";
  }

  @Nullable
  @Override
  protected RefactoringEventData getBeforeData() {
    RefactoringEventData data = new RefactoringEventData();
    data.addElement(myClass);
    return data;
  }

  @Nullable
  @Override
  protected RefactoringEventData getAfterData(UsageInfo @NotNull [] usages) {
    RefactoringEventData data = new RefactoringEventData();
    data.addElement(myClass);
    return data;
  }

  private void doRefactoring(UsageInfo[] usages) throws IncorrectOperationException {
    final PsiTypeParameter[] typeParameters = myClass.getTypeParameters();
    final boolean[] toRemoveParms = detectRemovedParameters(typeParameters);

    for (final UsageInfo usage : usages) {
      LOG.assertTrue(usage.getElement() instanceof PsiJavaCodeReferenceElement);
      processUsage(usage, typeParameters, toRemoveParms);
    }
    final Map<PsiTypeElement, PsiClass> supersMap = new HashMap<>();
    myClass.accept(new JavaRecursiveElementWalkingVisitor() {
      @Override
      public void visitTypeElement(@NotNull PsiTypeElement typeElement) {
        super.visitTypeElement(typeElement);
        final PsiType type = typeElement.getType();
        final PsiClass psiClass = PsiUtil.resolveClassInType(type);
        if (psiClass instanceof PsiTypeParameter) {
          final int i = ArrayUtil.find(typeParameters, psiClass);
          if ( i >= 0 && i < toRemoveParms.length && toRemoveParms[i]) {
           supersMap.put(typeElement, psiClass.getSuperClass());
          }
        }
      }
    });
    final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(myProject);
    for (Map.Entry<PsiTypeElement, PsiClass> classEntry : supersMap.entrySet()) {
      classEntry.getKey().replace(elementFactory.createTypeElement(elementFactory.createType(classEntry.getValue())));
    }
    changeClassSignature(typeParameters, toRemoveParms);
  }

  private void changeClassSignature(final PsiTypeParameter[] originalTypeParameters, boolean[] toRemoveParms)
    throws IncorrectOperationException {
    List<PsiTypeParameter> newTypeParameters = new ArrayList<>();
    for (final TypeParameterInfo info : myNewSignature) {
      newTypeParameters.add(info.getTypeParameter(originalTypeParameters, myProject));
    }
    final PsiTypeParameterList parameterList = myClass.getTypeParameterList();
    ChangeSignatureUtil.synchronizeList(parameterList, newTypeParameters, TypeParameterList.INSTANCE, toRemoveParms);
    JavaCodeStyleManager.getInstance(myProject).shortenClassReferences(parameterList);
  }

  private boolean[] detectRemovedParameters(final PsiTypeParameter[] original) {
    final boolean[] toRemove = new boolean[original.length];
    Arrays.fill(toRemove, true);
    for (final TypeParameterInfo info : myNewSignature) {
      if (info instanceof Existing) {
        toRemove[((Existing)info).getParameterIndex()] = false;
      }
    }
    return toRemove;
  }

  private void processUsage(UsageInfo usage, PsiTypeParameter[] original, boolean[] toRemove) throws IncorrectOperationException {
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(myClass.getProject());
    PsiJavaCodeReferenceElement referenceElement = (PsiJavaCodeReferenceElement)usage.getElement();
    assert referenceElement != null : usage;
    PsiSubstitutor usageSubstitutor = determineUsageSubstitutor(referenceElement);

    PsiReferenceParameterList referenceParameterList = referenceElement.getParameterList();
    assert referenceParameterList != null : referenceElement;
    PsiTypeElement[] oldValues = referenceParameterList.getTypeParameterElements();
    if (oldValues.length != original.length) return;
    List<PsiTypeElement> newValues = new ArrayList<>();
    for (final TypeParameterInfo info : myNewSignature) {
      if (info instanceof Existing) {
        newValues.add(oldValues[((Existing)info).getParameterIndex()]);
      }
      else {
        PsiType type = ((New)info).getDefaultValue().getType(myClass.getLBrace(), PsiManager.getInstance(myProject));
        PsiTypeElement newValue = factory.createTypeElement(usageSubstitutor.substitute(type));
        newValues.add(newValue);
      }
    }

    ChangeSignatureUtil.synchronizeList(referenceParameterList, newValues, ReferenceParameterList.INSTANCE, toRemove);
    JavaCodeStyleManager.getInstance(myProject).shortenClassReferences(referenceParameterList);
  }

  private PsiSubstitutor determineUsageSubstitutor(PsiJavaCodeReferenceElement referenceElement) {
    PsiType[] typeArguments = referenceElement.getTypeParameters();
    PsiSubstitutor usageSubstitutor = PsiSubstitutor.EMPTY;
    PsiTypeParameter[] typeParameters = myClass.getTypeParameters();
    if (typeParameters.length == typeArguments.length) {
      for (int i = 0; i < typeParameters.length; i++) {
        usageSubstitutor = usageSubstitutor.put(typeParameters[i], typeArguments[i]);
      }
    }
    return usageSubstitutor;
  }

  private static class ReferenceParameterList implements ChangeSignatureUtil.ChildrenGenerator<PsiReferenceParameterList, PsiTypeElement> {
    private static final ReferenceParameterList INSTANCE = new ReferenceParameterList();

    @Override
    public List<PsiTypeElement> getChildren(PsiReferenceParameterList list) {
      return Arrays.asList(list.getTypeParameterElements());
    }
  }

  private static class TypeParameterList implements ChangeSignatureUtil.ChildrenGenerator<PsiTypeParameterList, PsiTypeParameter> {
    private static final TypeParameterList INSTANCE = new TypeParameterList();

    @Override
    public List<PsiTypeParameter> getChildren(PsiTypeParameterList psiTypeParameterList) {
      return Arrays.asList(psiTypeParameterList.getTypeParameters());
    }
  }

}
