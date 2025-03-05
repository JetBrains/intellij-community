// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeserver.highlighting;

import com.intellij.java.codeserver.core.JavaPsiModifierUtil;
import com.intellij.java.codeserver.highlighting.errors.JavaErrorKinds;
import com.intellij.pom.java.JavaFeature;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.util.FileTypeUtils;
import com.intellij.psi.util.JavaPsiRecordUtil;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Set;

final class ModifierChecker {
  private static final Set<String> ourConstructorNotAllowedModifiers =
    Set.of(PsiModifier.ABSTRACT, PsiModifier.STATIC, PsiModifier.NATIVE, PsiModifier.FINAL, PsiModifier.STRICTFP, PsiModifier.SYNCHRONIZED);

  private final @NotNull JavaErrorVisitor myVisitor;

  ModifierChecker(@NotNull JavaErrorVisitor visitor) { myVisitor = visitor; }

  void checkNotAllowedModifier(@NotNull PsiKeyword keyword, @NotNull PsiModifierList modifierList) {
    PsiElement modifierOwner = modifierList.getParent();
    if (modifierOwner == null) return;
    @SuppressWarnings("MagicConstant")
    @PsiModifier.ModifierConstant String modifier = keyword.getText();
    
    PsiElement modifierOwnerParent =
      modifierOwner instanceof PsiMember psiMember ? psiMember.getContainingClass() : modifierOwner.getParent();
    if (modifierOwnerParent == null) modifierOwnerParent = modifierOwner.getParent();
    boolean isAllowed = true;
    if (modifierOwner instanceof PsiClass aClass) {
      boolean privateOrProtected = PsiModifier.PRIVATE.equals(modifier) || PsiModifier.PROTECTED.equals(modifier);
      if (aClass.isInterface()) {
        if (PsiModifier.STATIC.equals(modifier) || privateOrProtected || PsiModifier.PACKAGE_LOCAL.equals(modifier)) {
          isAllowed = modifierOwnerParent instanceof PsiClass;
        }
        if (PsiModifier.PUBLIC.equals(modifier)) {
          isAllowed = !(modifierOwnerParent instanceof PsiDeclarationStatement);
        }
        if (PsiModifier.SEALED.equals(modifier)) {
          isAllowed = !aClass.isAnnotationType();
        }
      }
      else {
        if (PsiModifier.PUBLIC.equals(modifier)) {
          isAllowed = modifierOwnerParent instanceof PsiImportHolder ||
                      // PsiJavaFile or JavaDummyHolder
                      modifierOwnerParent instanceof PsiClass psiClass &&
                      (modifierOwnerParent instanceof PsiSyntheticClass ||
                       PsiUtil.isAvailable(JavaFeature.INNER_STATICS, modifierOwnerParent) ||
                       psiClass.getQualifiedName() != null ||
                       !modifierOwnerParent.isPhysical());
        }
        else {
          if (PsiModifier.STATIC.equals(modifier) || privateOrProtected || PsiModifier.PACKAGE_LOCAL.equals(modifier)) {
            isAllowed = modifierOwnerParent instanceof PsiClass psiClass &&
                        (PsiModifier.STATIC.equals(modifier) ||
                         PsiUtil.isAvailable(JavaFeature.INNER_STATICS, modifierOwnerParent) ||
                         psiClass.getQualifiedName() != null) ||
                        FileTypeUtils.isInServerPageFile(modifierOwnerParent) ||
                        // non-physical dummy holder might not have FQN
                        !modifierOwnerParent.isPhysical();
          }
        }

        if (aClass.isEnum()) {
          isAllowed &=
            !PsiModifier.FINAL.equals(modifier) && !PsiModifier.ABSTRACT.equals(modifier) && !PsiModifier.SEALED.equals(modifier)
            && !PsiModifier.VALUE.equals(modifier);
        }
        else if (aClass.isRecord()) {
          isAllowed &= !PsiModifier.ABSTRACT.equals(modifier);
        }

        if (aClass.getContainingClass() instanceof PsiAnonymousClass &&
            privateOrProtected && !PsiUtil.getLanguageLevel(modifierOwnerParent).isAtLeast(LanguageLevel.JDK_16)) {
          isAllowed = false;
        }
      }
      if ((PsiModifier.NON_SEALED.equals(modifier) || PsiModifier.SEALED.equals(modifier)) &&
          modifierOwnerParent instanceof PsiDeclarationStatement) {
        // JLS 14.3
        myVisitor.report(JavaErrorKinds.MODIFIER_NOT_ALLOWED_LOCAL_CLASS.create(keyword, modifier));
        return;
      }
      else if (PsiModifier.NON_SEALED.equals(modifier) && !aClass.hasModifierProperty(PsiModifier.SEALED)) {
        isAllowed = Arrays.stream(aClass.getSuperTypes())
          .map(PsiClassType::resolve)
          .anyMatch(superClass -> superClass != null && superClass.hasModifierProperty(PsiModifier.SEALED));
        if (!isAllowed) {
          myVisitor.report(JavaErrorKinds.MODIFIER_NOT_ALLOWED_NON_SEALED.create(keyword, modifier));
          return;
        }
      }
    }
    else if (modifierOwner instanceof PsiMethod method) {
      isAllowed = !(method.isConstructor() && ourConstructorNotAllowedModifiers.contains(modifier));
      PsiClass containingClass = method.getContainingClass();
      if ((method.hasModifierProperty(PsiModifier.PUBLIC) || method.hasModifierProperty(PsiModifier.PROTECTED)) && method.isConstructor() &&
          containingClass != null && containingClass.isEnum()) {
        isAllowed = false;
      }

      boolean isInterface = modifierOwnerParent instanceof PsiClass psiClass && psiClass.isInterface();
      if (PsiModifier.PRIVATE.equals(modifier) && modifierOwnerParent instanceof PsiClass psiClass) {
        isAllowed &= !isInterface || PsiUtil.isAvailable(JavaFeature.PRIVATE_INTERFACE_METHODS, modifierOwner) && !psiClass.isAnnotationType();
      }
      else if (PsiModifier.STRICTFP.equals(modifier)) {
        isAllowed &= !isInterface || PsiUtil.isAvailable(JavaFeature.EXTENSION_METHODS, modifierOwner);
      }
      else if (PsiModifier.PROTECTED.equals(modifier) ||
               PsiModifier.TRANSIENT.equals(modifier) ||
               PsiModifier.FINAL.equals(modifier)) {
        isAllowed &= !isInterface;
      }
      else if (PsiModifier.SYNCHRONIZED.equals(modifier)) {
        isAllowed &= !isInterface && (containingClass == null || !containingClass.isValueClass());
      }

      if (containingClass != null && (containingClass.isInterface() || containingClass.isRecord())) {
        isAllowed &= !PsiModifier.NATIVE.equals(modifier);
      }

      if (containingClass != null && containingClass.isAnnotationType()) {
        isAllowed &= !PsiModifier.STATIC.equals(modifier);
        isAllowed &= !PsiModifier.DEFAULT.equals(modifier);
      }

      if (JavaPsiRecordUtil.getRecordComponentForAccessor(method) != null) {
        isAllowed &= !PsiModifier.STATIC.equals(modifier);
      }
    }
    else if (modifierOwner instanceof PsiField) {
      if (PsiModifier.PRIVATE.equals(modifier) || PsiModifier.PROTECTED.equals(modifier) || PsiModifier.TRANSIENT.equals(modifier) ||
          PsiModifier.STRICTFP.equals(modifier)) {
        isAllowed = modifierOwnerParent instanceof PsiClass psiClass && !psiClass.isInterface();
      }
    }
    else if (modifierOwner instanceof PsiClassInitializer) {
      isAllowed = PsiModifier.STATIC.equals(modifier);
    }
    else if (modifierOwner instanceof PsiLocalVariable || modifierOwner instanceof PsiParameter) {
      isAllowed = PsiModifier.FINAL.equals(modifier);
    }
    else if (modifierOwner instanceof PsiReceiverParameter || modifierOwner instanceof PsiRecordComponent) {
      isAllowed = false;
    }

    if (isAllowed && !JavaPsiModifierUtil.isAllowed(modifier, modifierList)) {
      isAllowed = false;
    }
    if (!isAllowed) {
      myVisitor.report(JavaErrorKinds.MODIFIER_NOT_ALLOWED.create(keyword, modifier));
    }
  }

  
  void checkIllegalModifierCombination(@NotNull PsiKeyword keyword, @NotNull PsiModifierList modifierList) {
    @SuppressWarnings("MagicConstant") @PsiModifier.ModifierConstant String modifier = keyword.getText();
    String incompatible = JavaPsiModifierUtil.getIncompatibleModifier(modifier, modifierList);
    if (incompatible != null) {
      if (incompatible.equals(modifier)) {
        for (PsiElement child = modifierList.getFirstChild(); child != null; child = child.getNextSibling()) {
          if (modifier.equals(child.getText())) {
            if (child == keyword) return;
            else break;
          }
        }
        myVisitor.report(JavaErrorKinds.MODIFIER_REPEATED.create(keyword, modifier));
      }
      else {
        myVisitor.report(JavaErrorKinds.MODIFIER_INCOMPATIBLE.create(keyword, incompatible));
      }
    }
  }

  void reportAccessProblem(@NotNull PsiJavaCodeReferenceElement ref,
                           @NotNull PsiModifierListOwner resolved,
                           @NotNull JavaResolveResult result) {
    result = withElement(result, resolved);
    if (resolved.hasModifierProperty(PsiModifier.PRIVATE)) {
      myVisitor.report(JavaErrorKinds.ACCESS_PRIVATE.create(ref, result));
      return;
    }

    if (resolved.hasModifierProperty(PsiModifier.PROTECTED)) {
      myVisitor.report(JavaErrorKinds.ACCESS_PROTECTED.create(ref, result));
      return;
    }

    PsiClass packageLocalClass = JavaPsiModifierUtil.getPackageLocalClassInTheMiddle(ref);
    if (packageLocalClass != null) {
      result = withElement(result, packageLocalClass);
    }

    if (resolved.hasModifierProperty(PsiModifier.PACKAGE_LOCAL) || packageLocalClass != null) {
      myVisitor.report(JavaErrorKinds.ACCESS_PACKAGE_LOCAL.create(ref, result));
      return;
    }

    checkModuleAccess(resolved, ref);
    if (myVisitor.hasErrorResults()) return;
    myVisitor.report(JavaErrorKinds.ACCESS_GENERIC_PROBLEM.create(ref, result));
  }

  private void checkModuleAccess(@NotNull PsiModifierListOwner resolved, @NotNull PsiElement ref) {
    // TODO: JPMS
  }

  private static @NotNull JavaResolveResult withElement(@NotNull JavaResolveResult original, @NotNull PsiElement newElement) {
    if (newElement == original.getElement()) return original;
    return new JavaResolveResult() {
      @Override
      public PsiElement getElement() {
        return newElement;
      }

      @Override
      public @NotNull PsiSubstitutor getSubstitutor() {
        return original.getSubstitutor();
      }

      @Override
      public boolean isPackagePrefixPackageReference() {
        return original.isPackagePrefixPackageReference();
      }

      @Override
      public boolean isAccessible() {
        return original.isAccessible();
      }

      @Override
      public boolean isStaticsScopeCorrect() {
        return original.isStaticsScopeCorrect();
      }

      @Override
      public PsiElement getCurrentFileResolveScope() {
        return original.getCurrentFileResolveScope();
      }

      @Override
      public boolean isValidResult() {
        return original.isValidResult();
      }
    };
  }
}
