// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.core;

import com.intellij.openapi.util.Comparing;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.SuggestedNameInfo;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.IncorrectOperationException;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Predicate;

public class CoreJavaCodeStyleManager extends JavaCodeStyleManager {
  @Override
  public boolean isStaticAutoImportName(@Nullable String fqn) {
    return false;
  }

  @Override
  public boolean addImport(@NotNull PsiJavaFile file, @NotNull PsiClass refClass) {
    return false;
  }

  @Override
  public @NotNull PsiElement shortenClassReferences(@NotNull PsiElement element,
                                                    @MagicConstant(flags = {DO_NOT_ADD_IMPORTS, INCOMPLETE_CODE}) int flags)
    throws IncorrectOperationException {
    return element;
  }

  @Override
  public @NotNull String getPrefixByVariableKind(@NotNull VariableKind variableKind) {
    return "";
  }

  @Override
  public @NotNull String getSuffixByVariableKind(@NotNull VariableKind variableKind) {
    return "";
  }

  @Override
  public int findEntryIndex(@NotNull PsiImportStatementBase statement) {
    return 0;
  }

  @Override
  public @NotNull PsiElement shortenClassReferences(@NotNull PsiElement element) throws IncorrectOperationException {
    return element;
  }

  @Override
  public void shortenClassReferences(@NotNull PsiElement element, int startOffset, int endOffset) throws IncorrectOperationException {
  }

  @Override
  public void optimizeImports(@NotNull PsiFile file) throws IncorrectOperationException {
  }

  @Override
  public PsiImportList prepareOptimizeImportsResult(@NotNull PsiJavaFile file) {
    return null;
  }

  @Override
  public @NotNull SuggestedNameInfo suggestVariableName(@NotNull VariableKind kind,
                                                        @Nullable String propertyName,
                                                        @Nullable PsiExpression expr,
                                                        @Nullable PsiType type,
                                                        boolean correctKeywords) {
    return SuggestedNameInfo.NULL_INFO;
  }

  @Override
  public @NotNull String variableNameToPropertyName(@NotNull @NonNls String name, @NotNull VariableKind variableKind) {
    return name;
  }

  @Override
  public @NotNull String propertyNameToVariableName(@NotNull @NonNls String propertyName, @NotNull VariableKind variableKind) {
    return propertyName;
  }

  @Override
  public @NotNull String suggestUniqueVariableName(@NotNull @NonNls String baseName, PsiElement place, boolean lookForward) {
    return suggestUniqueVariableName(baseName, place, lookForward, v -> false, null);
  }

  @Override
  public @NotNull String suggestUniqueVariableName(@NotNull String baseName,
                                                   PsiElement place,
                                                   boolean lookForward,
                                                   @NotNull Set<String> skipNames) {
    return suggestUniqueVariableName(baseName, place, lookForward, v -> false, v -> !skipNames.contains(v));
  }

  private static @NotNull String suggestUniqueVariableName(@NotNull @NonNls String baseName,
                                                           PsiElement place,
                                                           boolean lookForward,
                                                           Predicate<? super PsiVariable> canBeReused,
                                                           @Nullable Predicate<String> additionalValidator) {
    int index = 0;
    PsiElement scope = PsiTreeUtil.getNonStrictParentOfType(place, PsiStatement.class, PsiCodeBlock.class, PsiMethod.class);
    NextName:
    while (true) {
      String name = baseName;
      if (index > 0) {
        name += index;
      }
      index++;
      if (additionalValidator != null && !additionalValidator.test(name)) {
        continue;
      }
      if (PsiUtil.isVariableNameUnique(name, place)) {
        if (lookForward) {
          final String name1 = name;
          PsiElement run = scope;
          while (run != null) {
            class CancelException extends RuntimeException {
            }
            try {
              run.accept(new JavaRecursiveElementWalkingVisitor() {
                @Override
                public void visitAnonymousClass(final @NotNull PsiAnonymousClass aClass) {
                }

                @Override public void visitVariable(@NotNull PsiVariable variable) {
                  if (name1.equals(variable.getName()) && !canBeReused.test(variable)) {
                    throw new CancelException();
                  }
                }
              });
            }
            catch (CancelException e) {
              continue NextName;
            }
            run = run.getNextSibling();
            if (scope instanceof PsiMethod) {//do not check next member for param name conflict
              break;
            }
          }

        }
        return name;
      }
    }
  }

  @Override
  public @NotNull String suggestUniqueVariableName(@NotNull String baseName, PsiElement place, Predicate<? super PsiVariable> canBeReused) {
    return suggestUniqueVariableName(baseName, place, true, canBeReused, null);
  }

  @Override
  public @NotNull SuggestedNameInfo suggestUniqueVariableName(final @NotNull SuggestedNameInfo baseNameInfo,
                                                              PsiElement place,
                                                              boolean ignorePlaceName,
                                                              boolean lookForward) {
    final String[] names = baseNameInfo.names;
    final LinkedHashSet<String> uniqueNames = new LinkedHashSet<>(names.length);
    for (String name : names) {
      if (ignorePlaceName && place instanceof PsiNamedElement) {
        final String placeName = ((PsiNamedElement)place).getName();
        if (Comparing.strEqual(placeName, name)) {
          uniqueNames.add(name);
          continue;
        }
      }
      uniqueNames.add(suggestUniqueVariableName(name, place, lookForward));
    }

    return new SuggestedNameInfo(ArrayUtilRt.toStringArray(uniqueNames)) {
      @Override
      public void nameChosen(String name) {
        baseNameInfo.nameChosen(name);
      }
    };
  }

  @Override
  public @NotNull PsiElement qualifyClassReferences(@NotNull PsiElement element) {
    return element;
  }

  @Override
  public void removeRedundantImports(@NotNull PsiJavaFile file) throws IncorrectOperationException {
  }

  @Override
  public Collection<PsiImportStatementBase> findRedundantImports(@NotNull PsiJavaFile file) {
    return null;
  }

  @Override
  public @NotNull Collection<String> suggestSemanticNames(@NotNull PsiExpression expression) {
    return Collections.emptyList();
  }

  @Override
  public @NotNull Collection<String> suggestSemanticNames(@NotNull PsiExpression expression, @NotNull VariableKind kind) {
    return Collections.emptyList();
  }

  @Override
  public @NotNull SuggestedNameInfo suggestNames(@NotNull Collection<String> semanticNames, @NotNull VariableKind kind, @Nullable PsiType type) {
    return SuggestedNameInfo.NULL_INFO;
  }
}
