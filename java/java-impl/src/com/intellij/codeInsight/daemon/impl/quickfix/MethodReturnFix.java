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
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.CodeInsightUtilBase;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInspection.IntentionAndQuickFixAction;
import com.intellij.ide.util.SuperMethodWarningUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.undo.UndoUtil;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.*;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.changeSignature.ChangeSignatureProcessor;
import com.intellij.refactoring.typeMigration.TypeMigrationLabeler;
import com.intellij.refactoring.typeMigration.TypeMigrationProcessor;
import com.intellij.refactoring.typeMigration.TypeMigrationRules;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class MethodReturnFix extends IntentionAndQuickFixAction {
  private final PsiMethod myMethod;
  private final PsiType myReturnType;
  private final boolean myFixWholeHierarchy;

  public MethodReturnFix(PsiMethod method, PsiType toReturn, boolean fixWholeHierarchy) {
    myMethod = method;
    myReturnType = toReturn;
    myFixWholeHierarchy = fixWholeHierarchy;
  }

  @NotNull
  public String getName() {
    return QuickFixBundle.message("fix.return.type.text",
                                  myMethod.getName(),
                                  myReturnType.getCanonicalText());
  }

  @NotNull
  public String getFamilyName() {
    return QuickFixBundle.message("fix.return.type.family");
  }

  public boolean isAvailable(@NotNull final Project project, final Editor editor, final PsiFile file) {
    return myMethod != null
        && myMethod.isValid()
        && myMethod.getManager().isInProject(myMethod)
        && myReturnType != null
        && myReturnType.isValid()
        && !TypeConversionUtil.isNullType(myReturnType)
        && myMethod.getReturnType() != null
        && !Comparing.equal(myReturnType, myMethod.getReturnType());
  }

  public void applyFix(final Project project, final PsiFile file, final Editor editor) {
    if (!CodeInsightUtilBase.prepareFileForWrite(myMethod.getContainingFile())) return;
    PsiMethod method = myMethod;
    if (myFixWholeHierarchy) {
      final PsiMethod superMethod = myMethod.findDeepestSuperMethod();
      if (superMethod != null) {
        final PsiType superReturnType = superMethod.getReturnType();
        if (superReturnType != null && !Comparing.equal(myReturnType, superReturnType)) {
          final PsiClass psiClass = PsiUtil.resolveClassInType(superReturnType);
          if (psiClass instanceof PsiTypeParameter && changeClassTypeArgument(project, (PsiTypeParameter)psiClass)) return;
          method = SuperMethodWarningUtil.checkSuperMethod(myMethod, RefactoringBundle.message("to.refactor"));
          if (method == null) return;
        }
      }
    }
    if (!CodeInsightUtilBase.prepareFileForWrite(method.getContainingFile())) return;
    ChangeSignatureProcessor processor = new ChangeSignatureProcessor(myMethod.getProject(),
                                                                      method,
        false, null,
        method.getName(),
        myReturnType,
        RemoveUnusedParameterFix.getNewParametersInfo(method, null));
    processor.run();
    if (method.getContainingFile() != file) {
      UndoUtil.markPsiFileForUndo(file);
    }
  }

  private boolean changeClassTypeArgument(Project project, PsiTypeParameter typeParameter) {
    final PsiTypeParameterListOwner owner = typeParameter.getOwner();
    if (owner instanceof PsiClass) {
      final PsiClass derivedClass = myMethod.getContainingClass();
      if (derivedClass == null) return true;
      PsiType returnType = myReturnType;
      if (returnType instanceof PsiPrimitiveType) {
        returnType = ((PsiPrimitiveType)returnType).getBoxedType(derivedClass);
      }
      final PsiSubstitutor superClassSubstitutor =
        TypeConversionUtil.getSuperClassSubstitutor((PsiClass)owner, derivedClass, PsiSubstitutor.EMPTY);
      final PsiSubstitutor substitutor = superClassSubstitutor.put(typeParameter, returnType);
      final TypeMigrationRules rules = new TypeMigrationRules(TypeMigrationLabeler.getElementType(derivedClass));
      rules.setMigrationRootType(JavaPsiFacade.getElementFactory(project).createType(((PsiClass)owner), substitutor));
      rules.setBoundScope(new LocalSearchScope(derivedClass));

      final PsiReferenceParameterList referenceParameterList = findTypeArgumentsList(owner, derivedClass);
      if (referenceParameterList == null) return true;
      final TypeMigrationProcessor processor = new TypeMigrationProcessor(project, referenceParameterList, rules);
      processor.setPreviewUsages(!ApplicationManager.getApplication().isUnitTestMode());
      processor.run();
      return true;
    }
    return false;
  }

  @Nullable
  private static PsiReferenceParameterList findTypeArgumentsList(final PsiTypeParameterListOwner owner, final PsiClass derivedClass) {
    PsiReferenceParameterList referenceParameterList = null;
    if (derivedClass instanceof PsiAnonymousClass) {
      referenceParameterList = ((PsiAnonymousClass)derivedClass).getBaseClassReference().getParameterList();
    } else {
      final PsiReferenceList implementsList = derivedClass.getImplementsList();
      if (implementsList != null) {
        referenceParameterList = extractReferenceParameterList(owner, implementsList);
      }
      if (referenceParameterList == null) {
        final PsiReferenceList extendsList = derivedClass.getExtendsList();
        if (extendsList != null) {
          referenceParameterList = extractReferenceParameterList(owner, extendsList);
        }
      }
    }
    return referenceParameterList;
  }

  @Nullable
  private static PsiReferenceParameterList extractReferenceParameterList(final PsiTypeParameterListOwner owner,
                                                                         final PsiReferenceList extendsList) {
    for (PsiJavaCodeReferenceElement referenceElement : extendsList.getReferenceElements()) {
      if (referenceElement.resolve() == owner) {
        return referenceElement.getParameterList();
      }
    }
    return null;
  }

}
