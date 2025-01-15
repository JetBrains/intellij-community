// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.codeStyle;

import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collection;
import java.util.Set;
import java.util.function.Predicate;

public abstract class JavaCodeStyleManager {
  public static JavaCodeStyleManager getInstance(Project project) {
    return project.getService(JavaCodeStyleManager.class);
  }

  public static final int DO_NOT_ADD_IMPORTS = 0x1000;
  public static final int INCOMPLETE_CODE = 0x2000;

  /**
   * Determines whether the specified fully qualified name (FQN) represents
   * a class that should be statically auto-imported in the context.
   *
   * @param fqn the fully qualified name of the target class, must not be null
   * @return true if the class is configured to be statically auto-imported, false otherwise
   */
  public abstract boolean isStaticAutoImportClass(@Nullable String fqn);

  public abstract boolean addImport(@NotNull PsiJavaFile file, @NotNull PsiClass refClass);
  public abstract @NotNull PsiElement shortenClassReferences(@NotNull PsiElement element,
                                                             @MagicConstant(flags = {DO_NOT_ADD_IMPORTS, INCOMPLETE_CODE}) int flags) throws IncorrectOperationException;

  public abstract @NotNull String getPrefixByVariableKind(@NotNull VariableKind variableKind);
  public abstract @NotNull String getSuffixByVariableKind(@NotNull VariableKind variableKind);

  public abstract int findEntryIndex(@NotNull PsiImportStatementBase statement);

  /**
   * Replaces fully-qualified class names in the contents of the specified element with
   * non-qualified names and adds import statements as necessary.
   *
   * @param element the element to shorten references in.
   * @return the element in the PSI tree after the shorten references operation corresponding to the original element.
   * @throws IncorrectOperationException if the file to shorten references in is read-only.
   */
  public abstract @NotNull PsiElement shortenClassReferences(@NotNull PsiElement element) throws IncorrectOperationException;

  /**
   * Replaces fully-qualified class names in a part of contents of the specified element with
   * non-qualified names and adds import statements as necessary.
   *
   * @param element     the element to shorten references in.
   * @param startOffset the start offset in the <b>element</b> of the part where class references are shortened.
   * @param endOffset   the end offset in the <b>element</b> of the part where class references are shortened.
   * @throws IncorrectOperationException if the file to shorten references in is read-only.
   */
  public abstract void shortenClassReferences(@NotNull PsiElement element, int startOffset, int endOffset) throws IncorrectOperationException;

  /**
   * Optimizes imports in the specified Java or JSP file.
   *
   * @param file the file to optimize the imports in.
   * @throws IncorrectOperationException if the file is read-only.
   */
  public abstract void optimizeImports(@NotNull PsiFile file) throws IncorrectOperationException;

  /**
   * Calculates the import list that would be substituted in the specified Java or JSP
   * file if an Optimize Imports operation was performed on it.
   *
   * @param file the file to calculate the import list for.
   * @return the calculated import list, or {@code null} when the file has no import list.
   */
  public abstract PsiImportList prepareOptimizeImportsResult(@NotNull PsiJavaFile file);

  /**
   * Single-static-import {@code import static classFQN.referenceName;} shadows on-demand static imports, like described
   * JLS 6.4.1
   * A single-static-import declaration d in a compilation unit c of package p that imports a {member} named n
   * shadows the declaration of any static {member} named n imported by a static-import-on-demand declaration in c, throughout c.
   *
   * @return true if file contains import which would be shadowed
   *         false otherwise
   */
  public boolean hasConflictingOnDemandImport(@NotNull PsiJavaFile file, @NotNull PsiClass psiClass, @NotNull String referenceName) {
    return false;
  }

  /**
   * Returns the kind of the specified variable (local, parameter, field, static field or static final field).
   *
   * @param variable the variable to get the kind for.
   * @return the variable kind.
   */
  public @NotNull VariableKind getVariableKind(@NotNull PsiVariable variable){
    if (variable instanceof PsiField) {
      if (variable.hasModifierProperty(PsiModifier.STATIC)) {
        if (variable.hasModifierProperty(PsiModifier.FINAL)) {
          return VariableKind.STATIC_FINAL_FIELD;
        }
        return VariableKind.STATIC_FIELD;
      }
      return VariableKind.FIELD;
    }
    else {
      if (variable instanceof PsiParameter) {
        if (((PsiParameter)variable).getDeclarationScope() instanceof PsiForeachStatement) {
          return VariableKind.LOCAL_VARIABLE;
        }
        return VariableKind.PARAMETER;
      }
      return VariableKind.LOCAL_VARIABLE;
    }
  }

  public SuggestedNameInfo suggestVariableName(final @NotNull VariableKind kind,
                                               final @Nullable String propertyName,
                                               final @Nullable PsiExpression expr,
                                               @Nullable PsiType type) {
    return suggestVariableName(kind, propertyName, expr, type, true);
  }

  /**
   * Generates compiled parameter name for given type.
   * Should not access indices due to performance reasons (e.g. see IDEA-116803)
   */
  public @Nullable String suggestCompiledParameterName(@NotNull PsiType type) {
    SuggestedNameInfo info = suggestVariableName(VariableKind.PARAMETER, null, null, type, true);
    return ContainerUtil.getFirstItem(Arrays.asList(info.names));
  }


  public abstract @NotNull SuggestedNameInfo suggestVariableName(@NotNull VariableKind kind,
                                                                 @Nullable String propertyName,
                                                                 @Nullable PsiExpression expr,
                                                                 @Nullable PsiType type,
                                                                 boolean correctKeywords);
  /**
   * Generates a stripped-down name (with no code style defined prefixes or suffixes, usable as
   * a property name) from the specified name of a variable of the specified kind.
   *
   * @param name         the name of the variable.
   * @param variableKind the kind of the variable.
   * @return the stripped-down name.
   */
  public abstract @NotNull String variableNameToPropertyName(@NonNls @NotNull String name, @NotNull VariableKind variableKind);

  /**
   * Appends code style defined prefixes and/or suffixes for the specified variable kind
   * to the specified variable name.
   *
   * @param propertyName the base name of the variable.
   * @param variableKind the kind of the variable.
   * @return the variable name.
   */
  public abstract @NotNull String propertyNameToVariableName(@NonNls @NotNull String propertyName, @NotNull VariableKind variableKind);

  /**
   * Suggests a unique name for the variable used at the specified location. The returned name is guaranteed to not shadow
   * the existing name.
   *
   * @param baseName    the base name for the variable.
   * @param place       the location where the variable will be used.
   * @param lookForward if true, the existing variables are searched in both directions; if false - only backward
   * @return the generated unique name,
   */
  public abstract @NotNull String suggestUniqueVariableName(@NonNls @NotNull String baseName, PsiElement place, boolean lookForward);

  /**
   * Please, use {@link com.siyeh.ig.psiutils.VariableNameGenerator#skipNames(Collection)} instead of the direct call of this method
   * Suggests a unique name for the variable used at the specified location. The returned name is guaranteed to not shadow
   * the existing name.
   *
   * @param baseName    the base name for the variable.
   * @param place       the location where the variable will be used.
   * @param lookForward if true, the existing variables are searched in both directions; if false - only backward
   * @param skipNames   the names which will not be used.
   * @return the generated unique name,
   */
  @ApiStatus.Internal
  public abstract @NotNull String suggestUniqueVariableName(@NonNls @NotNull String baseName, PsiElement place, boolean lookForward,
                                                   @NotNull Set<@NotNull String> skipNames);

  /**
   * Suggests a unique names for the variable used at the specified location. The resulting name info may contain names which
   * shadow existing names.
   *
   * @param baseNameInfo the base name info for the variable.
   * @param place        the location where the variable will be used.
   * @param lookForward  if true, the existing variables are searched in both directions; if false - only backward
   * @return the generated unique name info.
   */
  public @NotNull SuggestedNameInfo suggestUniqueVariableName(@NotNull SuggestedNameInfo baseNameInfo,
                                                     PsiElement place,
                                                     boolean lookForward) {
    return suggestUniqueVariableName(baseNameInfo, place, false, lookForward);
  }

  /**
   * Suggests a unique name for the variable used at the specified location looking forward with possible filtering.
   *
   * @param baseName    the base name info for the variable.
   * @param place       the location where the variable will be used.
   * @param canBeReused a predicate which returns true for variables which names still could be reused (e.g. a variable will be deleted
   *                    during the ongoing refactoring)
   * @return the generated unique name
   */
  public abstract @NotNull String suggestUniqueVariableName(@NotNull String baseName, PsiElement place, Predicate<? super PsiVariable> canBeReused);

  /**
   * Suggests a unique name for the variable used at the specified location.
   *
   * @param baseNameInfo    the base name info for the variable.
   * @param place           the location where the variable will be used.
   * @param ignorePlaceName if true and place is PsiNamedElement, place.getName() would be still treated as unique name
   * @param lookForward     if true, the existing variables are searched in both directions; if false - only backward
   * @return the generated unique name
   */

  public abstract @NotNull SuggestedNameInfo suggestUniqueVariableName(@NotNull SuggestedNameInfo baseNameInfo,
                                                              PsiElement place,
                                                              boolean ignorePlaceName,
                                                              boolean lookForward);

  /**
   * Replaces all references to Java classes in the contents of the specified element,
   * except for references to classes in the same package or in implicitly imported packages,
   * with full-qualified references.
   *
   * @param element the element to replace the references in.
   * @return the element in the PSI tree after the qualify operation corresponding to the original element.
   */
  public abstract @NotNull PsiElement qualifyClassReferences(@NotNull PsiElement element);

  /**
   * Removes unused import statements from the specified Java file.
   *
   * @param file the file to remove the import statements from.
   * @throws IncorrectOperationException if the operation fails for some reason (for example, the file is read-only).
   */
  public abstract void removeRedundantImports(@NotNull PsiJavaFile file) throws IncorrectOperationException;

  public abstract @Nullable Collection<PsiImportStatementBase> findRedundantImports(@NotNull PsiJavaFile file);

  /**
   * This method is not actually tied to Java Code Style.
   * This method doesn't add prefixes or suffixes, and doesn't apply keyword correction.
   * Returned names already have proper pluralization.
   * <p>
   * Should be used with {@link #suggestNames(Collection, VariableKind, PsiType)}.
   */
  public abstract @NotNull Collection<String> suggestSemanticNames(@NotNull PsiExpression expression);

  /**
   * This method is not actually tied to Java Code Style and work similarly to {@link #suggestSemanticNames(PsiExpression)}
   * Additionally, this method adds new names from the context, based on VariableKind
   * <p>
   * Should be used with {@link #suggestNames(Collection, VariableKind, PsiType)}.
   */
  public abstract @NotNull Collection<String> suggestSemanticNames(@NotNull PsiExpression expression, @NotNull VariableKind kind);

  public abstract @NotNull SuggestedNameInfo suggestNames(@NotNull Collection<String> semanticNames,
                                                          @NotNull VariableKind kind,
                                                          @Nullable PsiType type);
}
