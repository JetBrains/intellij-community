// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.intention.BaseElementAtCaretIntentionAction;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.codeStyle.ImportHelper;
import com.intellij.psi.impl.source.tree.java.PsiReferenceExpressionImpl;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.MethodSignatureUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ObjectUtils;
import com.siyeh.ig.psiutils.CommentTracker;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public class AddSingleMemberStaticImportAction extends BaseElementAtCaretIntentionAction {
  private static final Logger LOG = Logger.getInstance(AddSingleMemberStaticImportAction.class);
  private static final Key<PsiElement> TEMP_REFERENT_USER_DATA = new Key<>("TEMP_REFERENT_USER_DATA");

  @Override
  @NotNull
  public String getFamilyName() {
    return JavaBundle.message("intention.add.single.member.static.import.family");
  }

  public static final class ImportAvailability {
    private final String qName;
    private final PsiMember resolved;

    private ImportAvailability(String qName, PsiMember resolved) {
      this.qName = qName;
      this.resolved = resolved;
    }
  }

  /**
   * Allows to check if it's possible to perform static import for the target element.
   *
   * @param element     target element that is static import candidate
   * @return            not-null qualified name of the class which method may be statically imported if any; {@code null} otherwise
   */
  @Nullable
  public static ImportAvailability getStaticImportClass(@NotNull PsiElement element) {
    if (!PsiUtil.isLanguageLevel5OrHigher(element)) return null;
    if (element instanceof PsiIdentifier) {
      final PsiElement parent = element.getParent();
      if (parent instanceof PsiMethodReferenceExpression) return null;
      if (parent instanceof PsiJavaCodeReferenceElement refExpr && refExpr.getQualifier() != null) {
        if (PsiTreeUtil.getParentOfType(parent, PsiImportList.class) != null) return null;
        if (checkParameterizedReference(refExpr)) return null;
        JavaResolveResult[] results = refExpr.multiResolve(false);
        for (JavaResolveResult result : results) {
          final PsiElement resolved = result.getElement();
          if (resolved instanceof PsiMember && ((PsiModifierListOwner)resolved).hasModifierProperty(PsiModifier.STATIC) ||
              resolved instanceof PsiClass) {
            if (!PsiUtil.isAccessible((PsiMember)resolved, element.getContainingFile(), null)) return null;
            PsiClass aClass = getResolvedClass(element, (PsiMember)resolved);
            String qName = aClass != null ? aClass.getQualifiedName() : null;
            if (aClass != null &&
                qName != null &&
                !PsiTreeUtil.isAncestor(aClass, element, true) &&
                !aClass.hasModifierProperty(PsiModifier.PRIVATE) &&
                !PsiUtil.isFromDefaultPackage(aClass)) {
              final PsiElement gParent = refExpr.getParent();
              if (gParent instanceof PsiMethodCallExpression) {
                final PsiMethodCallExpression call = (PsiMethodCallExpression)gParent.copy();
                final PsiElement qualifier = call.getMethodExpression().getQualifier();
                if (qualifier == null) return null;
                qualifier.delete();
                final JavaResolveResult resolveResult = call.resolveMethodGenerics();
                final PsiElement method = resolveResult.getElement();
                if (method instanceof PsiMethod) {
                  if (((PsiMethod)method).getContainingClass() != aClass) {
                    final PsiElement currentFileResolveScope = resolveResult.getCurrentFileResolveScope();
                    if (currentFileResolveScope instanceof PsiImportStaticStatement) {
                      //don't hide another on-demand import and don't create ambiguity
                      if (resolved instanceof PsiMethod && MethodSignatureUtil.areSignaturesEqual((PsiMethod)method, (PsiMethod)resolved)) {
                        return null;
                      }
                    }
                    else return null;
                  }
                }
                else if (method == null && call.getMethodExpression().multiResolve(false).length > 0) {
                  return null;
                }
              }
              else {
                PsiElement refNameElement = refExpr.getReferenceNameElement();
                if (refNameElement == null) return null;
                final PsiJavaCodeReferenceElement copy = JavaPsiFacade.getElementFactory(refNameElement.getProject())
                  .createReferenceFromText(refNameElement.getText(), refExpr);
                final PsiElement target = copy.resolve();
                if (target != null && PsiTreeUtil.getParentOfType(target, PsiClass.class) != aClass) return null;
                if (hasTypeArguments(refExpr.getQualifier())) return null;
              }
              return new ImportAvailability(qName + "." +refExpr.getReferenceName(), (PsiMember) resolved);
            }
          }
        }
      }
    }

    return null;
  }

  private static boolean hasTypeArguments(PsiElement qualifier) {
    return !PsiTreeUtil.processElements(qualifier, element -> !(element instanceof PsiReferenceParameterList) || 
                                                              ((PsiReferenceParameterList)element).getTypeParameterElements().length == 0);
  }

  private static PsiImportStatementBase findExistingImport(PsiFile file, PsiClass aClass, String refName) {
    if (file instanceof PsiJavaFile && aClass != null) {
      PsiImportList importList = ((PsiJavaFile)file).getImportList();
      if (importList != null) {
        for (PsiImportStaticStatement staticStatement : importList.getImportStaticStatements()) {
          if (staticStatement.isOnDemand()) {
            if (aClass.isEquivalentTo(staticStatement.resolveTargetClass())) {
              return staticStatement;
            }
          }
        }

        final PsiImportStatementBase importStatement = importList.findSingleImportStatement(refName);
        if (importStatement instanceof PsiImportStaticStatement &&
            aClass.isEquivalentTo(((PsiImportStaticStatement)importStatement).resolveTargetClass())) {
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
        else if (qResolved instanceof PsiClass) {
          aClass = (PsiClass)qResolved;
        }
      }
    }
    return aClass;
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, @NotNull PsiElement element) {
    ImportAvailability availability = getStaticImportClass(element);
    if (availability != null) {
      if (availability.resolved instanceof PsiClass) {
        setText(JavaBundle.message("intention.add.single.member.import.text", availability.qName));
      } else {
        PsiFile file = element.getContainingFile();
        if (!(file instanceof PsiJavaFile)) return false;
        PsiImportStatementBase existingImport =
          findExistingImport(file, availability.resolved.getContainingClass(), StringUtil.getShortName(availability.qName));
        if (existingImport != null && !existingImport.isOnDemand()) {
          setText(JavaBundle.message("intention.use.single.member.static.import.text" , availability.qName));
        }
        else {
          setText(JavaBundle.message("intention.add.single.member.static.import.text", availability.qName));
        }
      }
    }
    return availability != null;
  }

  public static void invoke(PsiFile file, final PsiElement element) {
    final PsiJavaCodeReferenceElement refExpr = ObjectUtils.tryCast(element.getParent(), PsiJavaCodeReferenceElement.class);
    if (refExpr == null) return;
    final String referenceName = refExpr.getReferenceName();
    final JavaResolveResult[] targets = refExpr.multiResolve(false);
    for (JavaResolveResult target : targets) {
      final PsiElement resolved = target.getElement();
      if (resolved != null) {
        bindAllClassRefs(file, resolved, referenceName, getResolvedClass(element, (PsiMember)resolved));
        return;
      }
    }
  }

  public static void bindAllClassRefs(final PsiFile file,
                                      @NotNull final PsiElement resolved,
                                      final String referenceName,
                                      final PsiClass resolvedClass) {
    file.accept(new JavaRecursiveElementWalkingVisitor() {
      @Override
      public void visitReferenceElement(@NotNull PsiJavaCodeReferenceElement reference) {
        super.visitReferenceElement(reference);

        if (referenceName != null && referenceName.equals(reference.getReferenceName())) {
          PsiElement resolved = reference.resolve();
          if (resolved != null) {
            reference.putUserData(TEMP_REFERENT_USER_DATA, resolved);
          }
        }
      }
    });

    PsiImportStatementBase existingImport = findExistingImport(file, resolvedClass, referenceName);
    if (existingImport == null && resolved instanceof PsiClass) {
      ((PsiImportHolder) file).importClass((PsiClass) resolved);
    }
    else if (existingImport == null || existingImport.isOnDemand() && resolvedClass != null && ImportHelper.hasConflictingOnDemandImport((PsiJavaFile)file, resolvedClass, referenceName)) {
      PsiReferenceExpressionImpl.bindToElementViaStaticImport(resolvedClass, referenceName, ((PsiJavaFile)file).getImportList());
    }

    file.accept(new JavaRecursiveElementVisitor() {
      @Override
      public void visitImportList(@NotNull PsiImportList list) {
      }

      @Override
      public void visitReferenceElement(@NotNull PsiJavaCodeReferenceElement reference) {

        try {
          if (checkParameterizedReference(reference)) return;

          if (referenceName.equals(reference.getReferenceName()) && !(reference instanceof PsiMethodReferenceExpression)) {
            final PsiElement qualifierExpression = reference.getQualifier();
            PsiElement referent = reference.getUserData(TEMP_REFERENT_USER_DATA);
            if (!reference.isQualified()) {
              if (referent instanceof PsiMember && referent != reference.resolve()) {
                try {
                  final PsiClass containingClass = ((PsiMember)referent).getContainingClass();
                  if (containingClass != null) {
                    reference = rebind(reference, containingClass);
                  }
                }
                catch (IncorrectOperationException e) {
                  LOG.error (e);
                }
              }
            }
            else if (referent == null ||
                     referent instanceof PsiClass ||
                     referent instanceof PsiMember && ((PsiMember)referent).hasModifierProperty(PsiModifier.STATIC)) {
              if (qualifierExpression instanceof PsiJavaCodeReferenceElement) {
                PsiElement aClass = ((PsiJavaCodeReferenceElement)qualifierExpression).resolve();
                if (aClass instanceof PsiVariable) {
                  aClass = PsiUtil.resolveClassInClassTypeOnly(((PsiVariable)aClass).getType());
                }
                if (aClass instanceof PsiClass && InheritanceUtil.isInheritorOrSelf((PsiClass)aClass, resolvedClass, true)) {
                  try {
                    new CommentTracker().deleteAndRestoreComments(qualifierExpression);
                  }
                  catch (IncorrectOperationException e) {
                    LOG.error(e);
                  }
                  if (referent == null ? reference.resolve() != null : !referent.isEquivalentTo(reference.resolve())) {
                    reference = rebind(reference, resolvedClass);
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

  private static PsiJavaCodeReferenceElement rebind(PsiJavaCodeReferenceElement reference, PsiClass targetClass) {
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(reference.getProject());
    PsiReferenceExpression copy = (PsiReferenceExpression)factory.createExpressionFromText("A." + reference.getReferenceName(), null);
    reference = (PsiReferenceExpression)reference.replace(copy);
    PsiReferenceExpression qualifier = Objects.requireNonNull((PsiReferenceExpression)reference.getQualifier());
    qualifier.bindToElement(targetClass);
    return reference;
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, @NotNull PsiElement element) throws IncorrectOperationException {
    invoke(element.getContainingFile(), element);
  }
}