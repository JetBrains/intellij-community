// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.ExpectedTypeInfo;
import com.intellij.codeInsight.ExpectedTypesProvider;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.intention.impl.BaseIntentionAction;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.codeInspection.util.IntentionName;
import com.intellij.ide.scratch.ScratchUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.PsiUtilCore;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class CreateClassFromUsageBaseFix extends BaseIntentionAction {
  protected static final Logger LOG = Logger.getInstance(CreateClassFromUsageBaseFix.class);
  protected CreateClassKind myKind;
  private final SmartPsiElementPointer<PsiJavaCodeReferenceElement> myRefElement;

  public CreateClassFromUsageBaseFix(CreateClassKind kind, final PsiJavaCodeReferenceElement refElement) {
    myKind = kind;
    myRefElement = SmartPointerManager.getInstance(refElement.getProject()).createSmartPsiElementPointer(refElement);
  }

  @Override
  public abstract @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile psiFile);

  protected abstract @IntentionName String getText(String varName);

  private boolean isAvailableInContext(final @NotNull PsiJavaCodeReferenceElement element) {
    PsiElement parent = element.getParent();

    if (myKind == CreateClassKind.ANNOTATION) {
      return parent instanceof PsiAnnotation;
    }

    if (parent instanceof PsiJavaCodeReferenceCodeFragment) return true;

    if (parent instanceof PsiTypeElement) {
      if (parent.getParent() instanceof PsiReferenceParameterList) return true;
      if (parent.getParent() instanceof PsiDeconstructionPattern) return true;

      while (parent.getParent() instanceof PsiTypeElement){
        parent = parent.getParent();
        if (parent.getParent() instanceof PsiReferenceParameterList) return true;
      }
      if (parent.getParent() instanceof PsiCodeFragment ||
          parent.getParent() instanceof PsiVariable ||
          parent.getParent() instanceof PsiMethod ||
          parent.getParent() instanceof PsiClassObjectAccessExpression ||
          parent.getParent() instanceof PsiTypeCastExpression) {
        return true;
      }
      PsiInstanceOfExpression instanceOfExpression = PsiTreeUtil.getParentOfType(parent, PsiInstanceOfExpression.class);
      if (instanceOfExpression != null && instanceOfExpression.getCheckType() == parent) {
        PsiType type = instanceOfExpression.getOperand().getType();
        if (type instanceof PsiArrayType) {
          return false;
        }

        if (type != null && (myKind == CreateClassKind.ENUM || myKind == CreateClassKind.RECORD)) {
          return type.accept(new PsiTypeVisitor<>() {
            @Override
            public Boolean visitType(@NotNull PsiType type) {
              return false;
            }

            @Override
            public Boolean visitClassType(@NotNull PsiClassType classType) {
              PsiClass aClass = classType.resolve();
              return aClass != null && aClass.isInterface();
            }

            @Override
            public Boolean visitWildcardType(@NotNull PsiWildcardType wildcardType) {
              PsiType bound = wildcardType.getBound();
              return bound == null || bound.accept(this);
            }
          });
        }
        return true;
      }
    }
    else if (parent instanceof PsiReferenceList) {
      if (myKind == CreateClassKind.ENUM || myKind == CreateClassKind.RECORD) return false;
      if (parent.getParent() instanceof PsiClass psiClass) {
        if (psiClass.getPermitsList() == parent) {
          if (myKind == CreateClassKind.INTERFACE && !psiClass.isInterface()) return false;
          return true;
        }
        if (psiClass.getExtendsList() == parent) {
          if (myKind == CreateClassKind.CLASS && !psiClass.isInterface()) return true;
          if (myKind == CreateClassKind.INTERFACE && psiClass.isInterface()) return true;
        }
        if (psiClass.getImplementsList() == parent && myKind == CreateClassKind.INTERFACE) return true;
      }
      else if (parent.getParent() instanceof PsiMethod method) {
        if (method.getThrowsList() == parent && myKind == CreateClassKind.CLASS) return true;
      }
    }
    else if (parent instanceof PsiAnonymousClass && ((PsiAnonymousClass)parent).getBaseClassReference() == element) {
      return true;
    }

    if (element instanceof PsiReferenceExpression) {
      if (parent instanceof PsiMethodCallExpression) {
        return false;
      }
      return !(parent.getParent() instanceof PsiMethodCallExpression) || myKind == CreateClassKind.CLASS;
    }
    return false;
  }

  private static boolean checkClassName(String name) {
    return Character.isUpperCase(name.charAt(0));
  }

  @Override
  public boolean isAvailable(final @NotNull Project project, final Editor editor, final PsiFile psiFile) {
    final PsiJavaCodeReferenceElement element = getRefElement();
    if (element == null ||
        (!element.getManager().isInProject(element) && !ScratchUtil.isScratch(PsiUtilCore.getVirtualFile(element)))) {
      return false;
    }
    JavaResolveResult[] results = element.multiResolve(true);
    if (results.length > 0 && results[0].getElement() instanceof PsiClass) {
      return false;
    }
    final String refName = element.getReferenceName();
    if (refName == null ||
        PsiTreeUtil.getParentOfType(element, PsiTypeElement.class, PsiReferenceList.class) == null && !checkClassName(refName)) return false;
    PsiElement nameElement = element.getReferenceNameElement();
    if (nameElement == null) return false;
    PsiElement parent = element.getParent();
    if (parent instanceof PsiExpression && !(parent instanceof PsiReferenceExpression)) return false;
    if (!isAvailableInContext(element)) return false;
    final String superClassName = getSuperClassName(element);
    if (superClassName != null) {
      if (superClassName.equals(CommonClassNames.JAVA_LANG_ENUM) && myKind != CreateClassKind.ENUM) return false;
      if (superClassName.equals(CommonClassNames.JAVA_LANG_RECORD) && myKind != CreateClassKind.RECORD) return false;
      final PsiClass psiClass = JavaPsiFacade.getInstance(project).findClass(superClassName, GlobalSearchScope.allScope(project));
      if (psiClass != null && psiClass.hasModifierProperty(PsiModifier.FINAL)) return false;
    }
    final int offset = editor.getCaretModel().getOffset();
    if (CreateFromUsageUtils.shouldShowTag(offset, nameElement, element)) {
      setText(getText(nameElement.getText()));
      return true;
    }

    return false;
  }

  @Override
  public @NotNull String getFamilyName() {
    return QuickFixBundle.message("create.class.from.usage.family");
  }

  protected @Nullable PsiJavaCodeReferenceElement getRefElement() {
    return myRefElement.getElement();
  }

  protected @Nullable String getSuperClassName(final PsiJavaCodeReferenceElement element) {
    String superClassName = null;
    PsiElement parent = element.getParent();
    final PsiElement ggParent = parent.getParent();
    if (ggParent instanceof PsiClass && ((PsiClass)ggParent).getPermitsList() == parent) {
      return ((PsiClass)ggParent).getQualifiedName();
    }
    else if (ggParent instanceof PsiMethod method) {
      if (method.getThrowsList() == parent) {
        superClassName = "java.lang.Exception";
      }
    } else if (ggParent instanceof PsiClassObjectAccessExpression) {
      final ExpectedTypeInfo[] expectedTypes = ExpectedTypesProvider.getExpectedTypes((PsiExpression)ggParent, false);
      if (expectedTypes.length == 1) {
        final PsiClassType.ClassResolveResult classResolveResult = PsiUtil.resolveGenericsClassInType(expectedTypes[0].getType());
        final PsiClass psiClass = classResolveResult.getElement();
        if (psiClass != null && CommonClassNames.JAVA_LANG_CLASS.equals(psiClass.getQualifiedName())) {
          final PsiTypeParameter[] typeParameters = psiClass.getTypeParameters();
          PsiType psiType = typeParameters.length == 1 ? classResolveResult.getSubstitutor().substitute(typeParameters[0]) : null;
          if (psiType instanceof PsiWildcardType && ((PsiWildcardType)psiType).isExtends()) {
            psiType = ((PsiWildcardType)psiType).getExtendsBound();
          }
          final PsiClass aClass = PsiUtil.resolveClassInType(psiType);
          if (aClass != null) return aClass.getQualifiedName();
        }
      }
    } else if (ggParent instanceof PsiExpressionList && parent instanceof PsiExpression &&
               (myKind == CreateClassKind.ENUM || myKind == CreateClassKind.RECORD)) {
      final ExpectedTypeInfo[] expectedTypes = ExpectedTypesProvider.getExpectedTypes((PsiExpression)parent, false);
      if (expectedTypes.length == 1) {
        final PsiClassType.ClassResolveResult classResolveResult = PsiUtil.resolveGenericsClassInType(expectedTypes[0].getType());
        final PsiClass psiClass = classResolveResult.getElement();
        if (psiClass != null && psiClass.isInterface()) {
          return psiClass.getQualifiedName();
        }
      }
      return null;
    }

    return superClassName;
  }

  protected static @Nullable PsiDeconstructionPattern getDeconstructionPattern(@NotNull PsiJavaCodeReferenceElement reference) {
    if (reference.getParent() instanceof PsiTypeElement typeElement &&
        typeElement.getParent() instanceof PsiDeconstructionPattern pattern) {
      return pattern;
    }
    return null;
  }
}
