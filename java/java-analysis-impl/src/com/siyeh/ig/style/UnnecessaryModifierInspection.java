// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.style;

import com.intellij.codeInspection.CleanupLocalInspectionTool;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.pom.java.JavaFeature;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiMethodUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.HardcodedMethodConstants;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.fixes.RemoveModifierFix;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.PropertyKey;

import java.util.List;

/**
 * @author Bas Leijdekkers
 */
public final class UnnecessaryModifierInspection extends BaseInspection implements CleanupLocalInspectionTool {

  @Override
  protected @NotNull String buildErrorString(Object... infos) {
    return (String)infos[0];
  }

  @Override
  protected @Nullable LocalQuickFix buildFix(Object... infos) {
    return new RemoveModifierFix((String)infos[1]);
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new UnnecessaryModifierVisitor();
  }

  private static class UnnecessaryModifierVisitor extends BaseInspectionVisitor {

    @Override
    public void visitClass(@NotNull PsiClass aClass) {
      final PsiElement parent = aClass.getParent();
      final boolean interfaceMember = parent instanceof PsiClass && ((PsiClass)parent).isInterface();
      final boolean redundantStrictfp = PsiUtil.isAvailable(JavaFeature.ALWAYS_STRICTFP, aClass) && aClass.hasModifierProperty(PsiModifier.STRICTFP);
      if (aClass.isRecord() || aClass.isInterface() || aClass.isEnum() || interfaceMember || redundantStrictfp) {
        PsiModifierList modifierList = aClass.getModifierList();
        if (modifierList == null) {
          return;
        }
        final List<PsiKeyword> modifiers = PsiTreeUtil.getChildrenOfTypeAsList(modifierList, PsiKeyword.class);
        for (PsiKeyword modifier : modifiers) {
          final IElementType tokenType = modifier.getTokenType();
          if (JavaTokenType.FINAL_KEYWORD == tokenType) {
            if (aClass.isRecord()) {
              // all records are implicitly final
              registerError(modifier, "unnecessary.record.modifier.problem.descriptor");
            }
          }
          else if (JavaTokenType.ABSTRACT_KEYWORD == tokenType) {
            if (aClass.isInterface()) {
              // all interfaces are implicitly abstract
              registerError(modifier, "unnecessary.interface.modifier.problem.descriptor");
            }
          }
          else if (JavaTokenType.STATIC_KEYWORD == tokenType) {
            if (parent instanceof PsiClass) {
              if (aClass.isRecord()) {
                // all inner records are implicitly static
                registerError(modifier, "unnecessary.inner.record.modifier.problem.descriptor");
              }
              else if (aClass.isInterface()) {
                // all inner interfaces are implicitly static
                registerError(modifier, "unnecessary.inner.interface.modifier.problem.descriptor");
              }
              else if (aClass.isEnum()) {
                // all inner enums are implicitly static
                registerError(modifier, "unnecessary.inner.enum.modifier.problem.descriptor");
              }
              else if (interfaceMember) {
                // all inner classes of interfaces are implicitly static
                registerError(modifier, "unnecessary.interface.inner.class.modifier.problem.descriptor");
              }
            }
          }
          else if (JavaTokenType.PUBLIC_KEYWORD == tokenType) {
            if (interfaceMember) {
              // all members of interfaces are implicitly public
              registerError(modifier, "unnecessary.interface.member.modifier.problem.descriptor");
            }
          }
          else if (JavaTokenType.STRICTFP_KEYWORD == tokenType) {
            if (redundantStrictfp) {
              // all code is strictfp under Java 17 and higher
              registerError(modifier, "unnecessary.strictfp.modifier.problem.descriptor");
            }
          }
        }
      }
    }

    @Override
    public void visitMethod(@NotNull PsiMethod method) {
      final boolean redundantStrictfp = PsiUtil.isAvailable(JavaFeature.ALWAYS_STRICTFP, method) && method.hasModifierProperty(PsiModifier.STRICTFP);
      if (redundantStrictfp) {
        final PsiModifierList modifierList = method.getModifierList();
        final List<PsiKeyword> modifiers = PsiTreeUtil.getChildrenOfTypeAsList(modifierList, PsiKeyword.class);
        for (PsiKeyword modifier : modifiers) {
          if (JavaTokenType.STRICTFP_KEYWORD == modifier.getTokenType()) {
            // all code is strictfp under Java 17 and higher
            registerError(modifier, "unnecessary.strictfp.modifier.problem.descriptor");
          }
        }
      }
      final PsiClass containingClass = method.getContainingClass();
      if (containingClass == null) {
        return;
      }
      if (containingClass.isEnum()) {
        if (!method.isConstructor() || !method.hasModifierProperty(PsiModifier.PRIVATE)) {
          return;
        }
        final PsiModifierList modifierList = method.getModifierList();
        final List<PsiKeyword> modifiers = PsiTreeUtil.getChildrenOfTypeAsList(modifierList, PsiKeyword.class);
        for (PsiKeyword modifier : modifiers) {
          if (JavaTokenType.PRIVATE_KEYWORD == modifier.getTokenType()) {
            // enum constructors are implicitly private
            registerError(modifier, "unnecessary.enum.constructor.modifier.problem.descriptor");
          }
        }
      }
      else if (containingClass.isInterface()) {
        final PsiModifierList modifierList = method.getModifierList();
        final List<PsiKeyword> modifiers = PsiTreeUtil.getChildrenOfTypeAsList(modifierList, PsiKeyword.class);
        for (PsiKeyword modifier : modifiers) {
          final IElementType tokenType = modifier.getTokenType();
          if (JavaTokenType.PUBLIC_KEYWORD == tokenType) {
            // all members of interface are implicitly public
            registerError(modifier, "unnecessary.interface.member.modifier.problem.descriptor");
          }
          else if (JavaTokenType.ABSTRACT_KEYWORD == tokenType) {
            // all non-default, non-static methods of interfaces are implicitly abstract
            registerError(modifier, "unnecessary.interface.method.modifier.problem.descriptor");
          }
        }
      }

      processMainMethod(method);
    }

    private void processMainMethod(@NotNull PsiMethod method) {
      if (PsiUtil.isAvailable(JavaFeature.IMPLICIT_CLASSES, method) &&
          HardcodedMethodConstants.MAIN.equals(method.getName()) &&
          PsiMethodUtil.isMainMethod(method)) {
        boolean isImplicitClass = method.getParent() instanceof PsiImplicitClass;
        final PsiModifierList modifierList = method.getModifierList();
        final List<PsiKeyword> modifiers = PsiTreeUtil.getChildrenOfTypeAsList(modifierList, PsiKeyword.class);
        LanguageLevel level = PsiUtil.getLanguageLevel(method);
        for (PsiKeyword modifier : modifiers) {
          if (modifier.getTokenType() == JavaTokenType.STATIC_KEYWORD && isImplicitClass) {
            //static for implicit class
            registerError(modifier, InspectionGadgetsBundle.message("unnecessary.main.modifier.problem.descriptor", level.getShortText()),
                          modifier.getText());
            continue;
          }
          if (modifier.getTokenType() == JavaTokenType.PUBLIC_KEYWORD ||
              modifier.getTokenType() == JavaTokenType.PROTECTED_KEYWORD) {
            if (isImplicitClass) {
              //public and protected for implicit class
              registerError(modifier, InspectionGadgetsBundle.message("unnecessary.main.modifier.problem.descriptor", level.getShortText()),
                            modifier.getText());
              continue;
            }
            if (isOnTheFly()) {
              final PsiSearchHelper searchHelper = PsiSearchHelper.getInstance(method.getProject());
              PsiClass containingClass = method.getContainingClass();
              if (containingClass == null || containingClass.getName() == null) return;
              final PsiSearchHelper.SearchCostResult cost =
                searchHelper.isCheapEnoughToSearch(containingClass.getName(), containingClass.getResolveScope(), null);
              if (cost == PsiSearchHelper.SearchCostResult.TOO_MANY_OCCURRENCES) {
                continue;
              }
            }

            PsiReference first = ReferencesSearch.search(method, method.getResolveScope()).findFirst();
            if (first != null) {
              continue;
            }
            //public and protected for normal class
            registerError(modifier, InspectionGadgetsBundle.message("unnecessary.main.modifier.problem.descriptor", level.getShortText()),
                          modifier.getText());
          }
        }
      }
    }

    @Override
    public void visitField(@NotNull PsiField field) {
      final PsiClass containingClass = field.getContainingClass();
      if (containingClass == null) {
        return;
      }
      if (containingClass.isInterface()) {
        final PsiModifierList modifierList = field.getModifierList();
        final List<PsiKeyword> modifiers = PsiTreeUtil.getChildrenOfTypeAsList(modifierList, PsiKeyword.class);
        for (PsiKeyword modifier : modifiers) {
          final IElementType tokenType = modifier.getTokenType();
          if (JavaTokenType.PUBLIC_KEYWORD == tokenType) {
            // all members of interfaces are implicitly public
            registerError(modifier, "unnecessary.interface.member.modifier.problem.descriptor");
          }
          else if (JavaTokenType.STATIC_KEYWORD == tokenType || JavaTokenType.FINAL_KEYWORD == tokenType) {
            // all fields of interfaces are implicitly static and final
            registerError(modifier, "unnecessary.interface.field.modifier.problem.descriptor");
          }
        }
      }
      else {
        // transient on interface field is a compile error
        if (field.hasModifierProperty(PsiModifier.STATIC) && field.hasModifierProperty(PsiModifier.TRANSIENT)) {
          final PsiModifierList modifierList = field.getModifierList();
          final List<PsiKeyword> modifiers = PsiTreeUtil.getChildrenOfTypeAsList(modifierList, PsiKeyword.class);
          for (PsiKeyword modifier : modifiers) {
            // a transient modifier on a static field is a no-op
            if (JavaTokenType.TRANSIENT_KEYWORD == modifier.getTokenType()) {
              registerError(modifier, "unnecessary.transient.modifier.problem.descriptor");
            }
          }
        }
      }
    }

    private void registerError(@NotNull PsiKeyword modifier,
                               @NotNull @PropertyKey(resourceBundle = InspectionGadgetsBundle.BUNDLE) String key) {
      registerError(modifier, InspectionGadgetsBundle.message(key), modifier.getText());
    }
  }
}
