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
import com.intellij.codeInsight.CodeInsightUtilBase;
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Key;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.java.PsiReferenceExpressionImpl;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
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
  public static String getStaticImportClass(@NotNull PsiElement element) {
    if (!PsiUtil.isLanguageLevel5OrHigher(element)) return null;
    PsiFile file = element.getContainingFile();
    if (element instanceof PsiIdentifier && element.getParent() instanceof PsiJavaCodeReferenceElement &&
        ((PsiJavaCodeReferenceElement)element.getParent()).getQualifier() != null) {
      PsiJavaCodeReferenceElement refExpr = (PsiJavaCodeReferenceElement)element.getParent();
      PsiReferenceParameterList parameterList = refExpr.getParameterList();
      if (parameterList != null && parameterList.getFirstChild() != null) return null;
      PsiElement resolved = refExpr.resolve();
      if (resolved instanceof PsiMember && ((PsiModifierListOwner)resolved).hasModifierProperty(PsiModifier.STATIC)) {
        PsiClass aClass = getResolvedClass(element, (PsiMember)resolved);
        if (aClass != null && !PsiTreeUtil.isAncestor(aClass, element, true) && !aClass.hasModifierProperty(PsiModifier.PRIVATE)) {
          String qName = aClass.getQualifiedName();
          if (qName != null && !Comparing.strEqual(qName, aClass.getName())) {
            qName = qName + "." +refExpr.getReferenceName();
            if (file instanceof PsiJavaFile) {
              PsiImportList importList = ((PsiJavaFile)file).getImportList();
              if (importList != null) {
                for (PsiImportStaticStatement staticStatement : importList.getImportStaticStatements()) {
                  if (staticStatement.isOnDemand()) {
                    if (staticStatement.resolveTargetClass() == aClass) {
                      return null;
                    }
                  }
                }
                if (importList.findSingleImportStatement(refExpr.getReferenceName()) == null) {
                  return qName;
                }
              }
            }
          }
        }
      }
    }

    return null;
  }

  @Nullable
  private static PsiClass getResolvedClass(PsiElement element, PsiMember resolved) {
    PsiClass aClass = resolved.getContainingClass();
    if (!PsiUtil.isAccessible(aClass, element, null)) {
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
    String classQName = getStaticImportClass(element);
    if (classQName != null) {
      setText(CodeInsightBundle.message("intention.add.single.member.static.import.text", classQName));
    }
    return classQName != null;
  }

  public static void invoke(PsiFile file, final PsiElement element) {
    if (!CodeInsightUtilBase.prepareFileForWrite(file)) return;
    
    final PsiJavaCodeReferenceElement refExpr = (PsiJavaCodeReferenceElement)element.getParent();
    final PsiElement resolved = refExpr.resolve();

    file.accept(new JavaRecursiveElementWalkingVisitor() {
      @Override
      public void visitReferenceElement(PsiJavaCodeReferenceElement reference) {
        super.visitReferenceElement(reference);
        String referenceName = refExpr.getReferenceName();
        if (referenceName != null && referenceName.equals(reference.getReferenceName())) {
          PsiElement resolved = reference.resolve();
          if (resolved != null) {
            reference.putUserData(TEMP_REFERENT_USER_DATA, resolved);
          }
        }
      }
    });

    if (resolved != null) {
      PsiReferenceExpressionImpl.bindToElementViaStaticImport(
        getResolvedClass(element, (PsiMember)resolved), ((PsiNamedElement)resolved).getName(), ((PsiJavaFile)file).getImportList()
      );
    }

    file.accept(new JavaRecursiveElementVisitor() {
      @Override
      public void visitReferenceElement(PsiJavaCodeReferenceElement reference) {

        if (reference.getParameterList() != null &&
            reference.getParameterList().getFirstChild() != null) return;

        if (refExpr.getReferenceName().equals(reference.getReferenceName())) {
          final PsiElement qualifierExpression = reference.getQualifier();
          PsiElement referent = reference.getUserData(TEMP_REFERENT_USER_DATA);
          if (!reference.isQualified()) {

            if (referent instanceof PsiMember && referent != reference.resolve()) {
              PsiElementFactory factory = JavaPsiFacade.getInstance(reference.getProject()).getElementFactory();
              try {
                PsiReferenceExpression copy = (PsiReferenceExpression)factory.createExpressionFromText("A." + reference.getReferenceName(), null);
                reference = (PsiReferenceExpression)reference.replace(copy);
                ((PsiReferenceExpression)reference.getQualifier()).bindToElement(((PsiMember)referent).getContainingClass());
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
              if (aClass instanceof PsiClass && InheritanceUtil.isInheritorOrSelf((PsiClass)aClass, getResolvedClass(element, (PsiMember)resolved), true)) {
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
        super.visitReferenceElement(reference);
      }
    });

  }
  
  @Override
  public void invoke(@NotNull final Project project, final Editor editor, PsiFile file) throws IncorrectOperationException {
    PsiElement element = file.findElementAt(editor.getCaretModel().getOffset());
    invoke(file, element);
  }
}
