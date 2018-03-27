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
package com.intellij.refactoring.changeClassSignature;

import com.intellij.history.LocalHistory;
import com.intellij.history.LocalHistoryAction;
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

/**
 * @author dsl
 */
public class ChangeClassSignatureProcessor extends BaseRefactoringProcessor {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.changeClassSignature.ChangeClassSignatureProcessor");
  private PsiClass myClass;
  private final TypeParameterInfo[] myNewSignature;

  public ChangeClassSignatureProcessor(Project project, PsiClass aClass, TypeParameterInfo[] newSignature) {
    super(project);
    myClass = aClass;
    myNewSignature = newSignature;
  }

  @Override
  protected void refreshElements(@NotNull PsiElement[] elements) {
    LOG.assertTrue(elements.length == 1);
    LOG.assertTrue(elements[0] instanceof PsiClass);
    myClass = (PsiClass)elements[0];
  }

  @NotNull
  protected String getCommandName() {
    return ChangeClassSignatureDialog.REFACTORING_NAME;
  }

  @NotNull
  protected UsageViewDescriptor createUsageViewDescriptor(@NotNull UsageInfo[] usages) {
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
        conflicts.putValue(myClass, RefactoringUIUtil.getDescription(myClass, false) + " already contains type parameter " + newName);
      }
      infos.put(newName, info);
    }
    return showConflicts(conflicts, refUsages.get());
  }

  @NotNull
  protected UsageInfo[] findUsages() {
    GlobalSearchScope projectScope = GlobalSearchScope.projectScope(myProject);
    List<UsageInfo> result = new ArrayList<>();

    boolean hadTypeParameters = myClass.hasTypeParameters();
    for (final PsiReference reference : ReferencesSearch.search(myClass, projectScope, false)) {
      if (reference.getElement() instanceof PsiJavaCodeReferenceElement) {
        PsiJavaCodeReferenceElement referenceElement = (PsiJavaCodeReferenceElement)reference.getElement();
        PsiElement parent = referenceElement.getParent();
        if (parent instanceof PsiTypeElement && (parent.getParent() instanceof PsiInstanceOfExpression ||
                                                 parent.getParent() instanceof PsiClassObjectAccessExpression)) {
          continue;
        }
        if (parent instanceof PsiNewExpression && PsiDiamondType.hasDiamond((PsiNewExpression)parent)) {
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
    return result.toArray(new UsageInfo[result.size()]);
  }

  protected void performRefactoring(@NotNull UsageInfo[] usages) {
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
  protected RefactoringEventData getAfterData(@NotNull UsageInfo[] usages) {
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
      public void visitTypeElement(PsiTypeElement typeElement) {
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
      if (info instanceof TypeParameterInfo.Existing) {
        toRemove[((TypeParameterInfo.Existing)info).getParameterIndex()] = false;
      }
    }
    return toRemove;
  }

  private void processUsage(UsageInfo usage, PsiTypeParameter[] original, boolean[] toRemove) throws IncorrectOperationException {
    PsiElementFactory factory = JavaPsiFacade.getInstance(myClass.getProject()).getElementFactory();
    PsiJavaCodeReferenceElement referenceElement = (PsiJavaCodeReferenceElement)usage.getElement();
    assert referenceElement != null : usage;
    PsiSubstitutor usageSubstitutor = determineUsageSubstitutor(referenceElement);

    PsiReferenceParameterList referenceParameterList = referenceElement.getParameterList();
    assert referenceParameterList != null : referenceElement;
    PsiTypeElement[] oldValues = referenceParameterList.getTypeParameterElements();
    if (oldValues.length != original.length) return;
    List<PsiTypeElement> newValues = new ArrayList<>();
    for (final TypeParameterInfo info : myNewSignature) {
      if (info instanceof TypeParameterInfo.Existing) {
        newValues.add(oldValues[((TypeParameterInfo.Existing)info).getParameterIndex()]);
      }
      else {
        PsiType type = ((TypeParameterInfo.New)info).getDefaultValue().getType(myClass.getLBrace(), PsiManager.getInstance(myProject));
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

    public List<PsiTypeElement> getChildren(PsiReferenceParameterList list) {
      return Arrays.asList(list.getTypeParameterElements());
    }
  }

  private static class TypeParameterList implements ChangeSignatureUtil.ChildrenGenerator<PsiTypeParameterList, PsiTypeParameter> {
    private static final TypeParameterList INSTANCE = new TypeParameterList();

    public List<PsiTypeParameter> getChildren(PsiTypeParameterList psiTypeParameterList) {
      return Arrays.asList(psiTypeParameterList.getTypeParameters());
    }
  }

}
