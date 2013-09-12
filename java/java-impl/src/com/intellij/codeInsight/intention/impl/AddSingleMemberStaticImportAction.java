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

/*
 * @author ven
 */
package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Key;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.java.PsiReferenceExpressionImpl;
import com.intellij.psi.util.*;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AddSingleMemberStaticImportAction extends PsiElementBaseIntentionAction {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.intention.impl.AddSingleMemberStaticImportAction");
  private static final Key<PsiElement> TEMP_REFERENT_USER_DATA = new Key<PsiElement>("TEMP_REFERENT_USER_DATA");

  @Override
  @NotNull
  public String getFamilyName() {
    return CodeInsightBundle.message("intention.add.single.member.static.import.family");
  }

  /**
   * Allows to check if it's possible to perform static import for the target element.
   *
   * @param element     target element that is static import candidate
   * @return            not-null qualified name of the class which method may be statically imported if any; <code>null</code> otherwise
   */
  @Nullable
  public static String getStaticImportClass(@NotNull PsiElement element, boolean useExisting) {
    if (!PsiUtil.isLanguageLevel5OrHigher(element)) return null;
    if (element instanceof PsiIdentifier) {
      final PsiElement parent = element.getParent();
      if (parent instanceof PsiMethodReferenceExpression) return null;
      if (parent instanceof PsiJavaCodeReferenceElement && ((PsiJavaCodeReferenceElement)parent).getQualifier() != null) {
        if (PsiTreeUtil.getParentOfType(parent, PsiImportList.class) != null) return null;
        PsiJavaCodeReferenceElement refExpr = (PsiJavaCodeReferenceElement)parent;
        if (checkParameterizedReference(refExpr)) return null;
        PsiElement resolved = refExpr.resolve();
        if (resolved instanceof PsiMember && ((PsiModifierListOwner)resolved).hasModifierProperty(PsiModifier.STATIC)) {
          PsiClass aClass = getResolvedClass(element, (PsiMember)resolved);
          if (aClass != null && !PsiTreeUtil.isAncestor(aClass, element, true) && !aClass.hasModifierProperty(PsiModifier.PRIVATE)) {
            if (findExistingImport(element.getContainingFile(), aClass, refExpr.getReferenceName()) == null || useExisting) {
              String qName = aClass.getQualifiedName();
              if (qName != null && !Comparing.strEqual(qName, aClass.getName())) {
                return qName + "." +refExpr.getReferenceName();

              }
            }
          }
        }
      }
    }

    return null;
  }

  private static PsiImportStatementBase findExistingImport(PsiFile file, PsiClass aClass, String refName) {
    if (file instanceof PsiJavaFile) {
      PsiImportList importList = ((PsiJavaFile)file).getImportList();
      if (importList != null) {
        for (PsiImportStaticStatement staticStatement : importList.getImportStaticStatements()) {
          if (staticStatement.isOnDemand()) {
            if (staticStatement.resolveTargetClass() == aClass) {
              return staticStatement;
            }
          }
        }

        final PsiImportStatementBase importStatement = importList.findSingleImportStatement(refName);
        if (importStatement instanceof PsiImportStaticStatement && 
            ((PsiImportStaticStatement)importStatement).resolveTargetClass() == aClass) {
          return importStatement;
        }
      }
    }
    return null;
  }

  private static boolean checkParameterizedReference(PsiJavaCodeReferenceElement refExpr) {
    PsiReferenceParameterList parameterList = refExpr instanceof PsiReferenceExpression ? refExpr.getParameterList() : null;
    return parameterList != null && parameterList.getFirstChild() != null;
  }

  @Nullable
  private static PsiClass getResolvedClass(PsiElement element, PsiMember resolved) {
    PsiClass aClass = resolved.getContainingClass();
    if (aClass != null && !PsiUtil.isAccessible(aClass.getProject(), aClass, element, null)) {
      final PsiElement qualifier = ((PsiJavaCodeReferenceElement)element.getParent()).getQualifier();
      if (qualifier instanceof PsiReferenceExpression) {
        final PsiElement qResolved = ((PsiReferenceExpression)qualifier).resolve();
        if (qResolved instanceof PsiVariable) {
          aClass = PsiUtil.resolveClassInClassTypeOnly(((PsiVariable)qResolved).getType());
        }
      }
    }
    return aClass;
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, @NotNull PsiElement element) {
    String classQName = getStaticImportClass(element, false);
    if (classQName != null) {
      setText(CodeInsightBundle.message("intention.add.single.member.static.import.text", classQName));
    }
    return classQName != null;
  }

  public static void invoke(PsiFile file, final PsiElement element) {
    if (!FileModificationService.getInstance().prepareFileForWrite(file)) return;

    final PsiJavaCodeReferenceElement refExpr = (PsiJavaCodeReferenceElement)element.getParent();
    final PsiElement resolved = refExpr.resolve();
    final String referenceName = refExpr.getReferenceName();
    bindAllClassRefs(file, resolved, referenceName, resolved != null ? getResolvedClass(element, (PsiMember)resolved) : null);
  }

  public static void bindAllClassRefs(final PsiFile file,
                                      final PsiElement resolved,
                                      final String referenceName,
                                      final PsiClass resolvedClass) {
    file.accept(new JavaRecursiveElementWalkingVisitor() {
      @Override
      public void visitReferenceElement(PsiJavaCodeReferenceElement reference) {
        super.visitReferenceElement(reference);

        if (referenceName != null && referenceName.equals(reference.getReferenceName())) {
          PsiElement resolved = reference.resolve();
          if (resolved != null) {
            reference.putUserData(TEMP_REFERENT_USER_DATA, resolved);
          }
        }
      }
    });

    if (resolved != null && findExistingImport(file, resolvedClass, referenceName) == null) {
      PsiReferenceExpressionImpl.bindToElementViaStaticImport(resolvedClass, referenceName, ((PsiJavaFile)file).getImportList());
    }

    file.accept(new JavaRecursiveElementVisitor() {
      @Override
      public void visitImportList(PsiImportList list) {
      }

      @Override
      public void visitReferenceElement(PsiJavaCodeReferenceElement reference) {

        try {
          if (checkParameterizedReference(reference)) return;

          if (referenceName.equals(reference.getReferenceName()) && !(reference instanceof PsiMethodReferenceExpression)) {
            final PsiElement qualifierExpression = reference.getQualifier();
            PsiElement referent = reference.getUserData(TEMP_REFERENT_USER_DATA);
            if (!reference.isQualified()) {
              if (referent instanceof PsiMember && referent != reference.resolve()) {
                PsiElementFactory factory = JavaPsiFacade.getInstance(reference.getProject()).getElementFactory();
                try {
                  final PsiClass containingClass = ((PsiMember)referent).getContainingClass();
                  if (containingClass != null) {
                    PsiReferenceExpression copy = (PsiReferenceExpression)factory.createExpressionFromText("A." + reference.getReferenceName(), null);
                    reference = (PsiReferenceExpression)reference.replace(copy);
                    ((PsiReferenceExpression)reference.getQualifier()).bindToElement(containingClass);
                  }
                }
                catch (IncorrectOperationException e) {
                  LOG.error (e);
                }
              }
              reference.putUserData(TEMP_REFERENT_USER_DATA, null);
            } else {
              if (qualifierExpression instanceof PsiJavaCodeReferenceElement) {
                PsiElement aClass = ((PsiJavaCodeReferenceElement)qualifierExpression).resolve();
                if (aClass instanceof PsiVariable) {
                  aClass = PsiUtil.resolveClassInClassTypeOnly(((PsiVariable)aClass).getType());
                }
                if (aClass instanceof PsiClass && InheritanceUtil.isInheritorOrSelf((PsiClass)aClass, resolvedClass, true)) {
                  boolean foundMemberByName = false;
                  if (referent instanceof PsiMember) {
                    final String memberName = ((PsiMember)referent).getName();
                    final PsiClass containingClass = PsiTreeUtil.getParentOfType(reference, PsiClass.class);
                    if (containingClass != null) {
                      foundMemberByName |= containingClass.findFieldByName(memberName, true) != null;
                      foundMemberByName |= containingClass.findMethodsByName(memberName, true).length > 0;
                    }
                  }
                  if (!foundMemberByName) {
                    try {
                      qualifierExpression.delete();
                    }
                    catch (IncorrectOperationException e) {
                      LOG.error(e);
                    }
                  }
                }
              }
            }
            reference.putUserData(TEMP_REFERENT_USER_DATA, null);
          }
        }
        finally {
          super.visitReferenceElement(reference);
        }
      }
    });
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, @NotNull PsiElement element) throws IncorrectOperationException {
    invoke(element.getContainingFile(), element);
  }
}
