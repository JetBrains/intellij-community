/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.changeSignature.ChangeSignatureUtil;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewDescriptor;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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

  protected void refreshElements(PsiElement[] elements) {
    LOG.assertTrue(elements.length == 1);
    LOG.assertTrue(elements[0] instanceof PsiClass);
    myClass = (PsiClass)elements[0];
  }

  protected String getCommandName() {
    return ChangeClassSignatureDialog.REFACTORING_NAME;
  }

  protected UsageViewDescriptor createUsageViewDescriptor(UsageInfo[] usages) {
    return new ChangeClassSigntaureViewDescriptor(myClass);
  }

  @NotNull
  protected UsageInfo[] findUsages() {
    GlobalSearchScope projectScope = GlobalSearchScope.projectScope(myProject);
    List<UsageInfo> result = new ArrayList<UsageInfo>();

    boolean hadTypeParameters = myClass.hasTypeParameters();
    for (final PsiReference reference : ReferencesSearch.search(myClass, projectScope, false)) {
      if (reference.getElement() instanceof PsiJavaCodeReferenceElement) {
        PsiJavaCodeReferenceElement referenceElement = (PsiJavaCodeReferenceElement)reference.getElement();
        PsiElement parent = referenceElement.getParent();
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

  protected void performRefactoring(UsageInfo[] usages) {
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

  private void doRefactoring(UsageInfo[] usages) throws IncorrectOperationException {
    final PsiTypeParameter[] typeParameters = myClass.getTypeParameters();
    boolean[] toRemoveParms = detectRemovedParameters(typeParameters);

    for (final UsageInfo usage : usages) {
      LOG.assertTrue(usage.getElement() instanceof PsiJavaCodeReferenceElement);
      processUsage(usage, typeParameters, toRemoveParms);
    }

    changeClassSignature(typeParameters, toRemoveParms);
  }

  private void changeClassSignature(final PsiTypeParameter[] originalTypeParameters, boolean[] toRemoveParms)
    throws IncorrectOperationException {
    PsiElementFactory factory = JavaPsiFacade.getInstance(myClass.getProject()).getElementFactory();
    List<PsiTypeParameter> newTypeParameters = new ArrayList<PsiTypeParameter>();
    for (final TypeParameterInfo info : myNewSignature) {
      int oldIndex = info.getOldParameterIndex();
      if (oldIndex >= 0) {
        newTypeParameters.add(originalTypeParameters[oldIndex]);
      }
      else {
        newTypeParameters.add(factory.createTypeParameterFromText(info.getNewName(), null));
      }
    }
    ChangeSignatureUtil.synchronizeList(myClass.getTypeParameterList(), newTypeParameters, TypeParameterList.INSTANCE, toRemoveParms);
  }

  private boolean[] detectRemovedParameters(final PsiTypeParameter[] originaltypeParameters) {
    final boolean[] toRemoveParms = new boolean[originaltypeParameters.length];
    Arrays.fill(toRemoveParms, true);
    for (final TypeParameterInfo info : myNewSignature) {
      int oldParameterIndex = info.getOldParameterIndex();
      if (oldParameterIndex >= 0) {
        toRemoveParms[oldParameterIndex] = false;
      }
    }
    return toRemoveParms;
  }

  private void processUsage(final UsageInfo usage, final PsiTypeParameter[] originalTypeParameters, final boolean[] toRemoveParms)
    throws IncorrectOperationException {
    PsiElementFactory factory = JavaPsiFacade.getInstance(myClass.getProject()).getElementFactory();
    PsiJavaCodeReferenceElement referenceElement = (PsiJavaCodeReferenceElement)usage.getElement();
    PsiSubstitutor usageSubstitutor = determineUsageSubstitutor(referenceElement);

    PsiReferenceParameterList referenceParameterList = referenceElement.getParameterList();
    PsiTypeElement[] oldValues = referenceParameterList.getTypeParameterElements();
    if (oldValues.length != originalTypeParameters.length) return;
    List<PsiTypeElement> newValues = new ArrayList<PsiTypeElement>();
    for (final TypeParameterInfo info : myNewSignature) {
      int oldIndex = info.getOldParameterIndex();
      if (oldIndex >= 0) {
        newValues.add(oldValues[oldIndex]);
      }
      else {
        PsiType type = info.getDefaultValue().getType(myClass.getLBrace(), PsiManager.getInstance(myProject));

        PsiTypeElement newValue = factory.createTypeElement(usageSubstitutor.substitute(type));
        newValues.add(newValue);
      }
    }
    ChangeSignatureUtil.synchronizeList(referenceParameterList, newValues, ReferenceParameterList.INSTANCE, toRemoveParms);
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
