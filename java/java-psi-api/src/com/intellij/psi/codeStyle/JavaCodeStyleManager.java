/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
 * @author max
 */
package com.intellij.psi.codeStyle;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

public abstract class JavaCodeStyleManager {
  public static JavaCodeStyleManager getInstance(Project project) {
    return ServiceManager.getService(project, JavaCodeStyleManager.class);
  }

  public static final int DO_NOT_ADD_IMPORTS = 0x1000;
  public static final int UNCOMPLETE_CODE = 0x2000;

  public abstract boolean addImport(@NotNull PsiJavaFile file, @NotNull PsiClass refClass);
  public abstract PsiElement shortenClassReferences(@NotNull PsiElement element, @MagicConstant(flags = {DO_NOT_ADD_IMPORTS, UNCOMPLETE_CODE}) int flags) throws IncorrectOperationException;

  @NotNull public abstract String getPrefixByVariableKind(VariableKind variableKind);
  @NotNull public abstract String getSuffixByVariableKind(VariableKind variableKind);

  public abstract int findEntryIndex(@NotNull PsiImportStatementBase statement);

  /**
   * Replaces fully-qualified class names in the contents of the specified element with
   * non-qualified names and adds import statements as necessary.
   *
   * @param element the element to shorten references in.
   * @return the element in the PSI tree after the shorten references operation corresponding
   *         to the original element.
   * @throws com.intellij.util.IncorrectOperationException if the file to shorten references in is read-only.
   */
  public abstract PsiElement shortenClassReferences(@NotNull PsiElement element) throws IncorrectOperationException;

  /**
   * Replaces fully-qualified class names in a part of contents of the specified element with
   * non-qualified names and adds import statements as necessary.
   *
   * @param element     the element to shorten references in.
   * @param startOffset the start offset in the <b>element</b> of the part where class references are
   *                    shortened.
   * @param endOffset   the end offset in the <b>element</b> of the part where class references are
   *                    shortened.
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
   * @return the calculated import list.
   */
  public abstract PsiImportList prepareOptimizeImportsResult(@NotNull PsiJavaFile file);

  /**
   * Returns the kind of the specified variable (local, parameter, field, static field or static
   * final field).
   *
   * @param variable the variable to get the kind for.
   * @return the variable kind.
   */
  public VariableKind getVariableKind(@NotNull PsiVariable variable){
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

  public SuggestedNameInfo suggestVariableName(@NotNull final VariableKind kind,
                                               @Nullable final String propertyName,
                                               @Nullable final PsiExpression expr,
                                               @Nullable PsiType type) {
    return suggestVariableName(kind, propertyName, expr, type, true);
  }

  /**
   * Generates compiled parameter name for given type.
   * Should not access indices due to performance reasons (e.g. see IDEA-116803)
   */
  public SuggestedNameInfo suggestCompiledParameterName(@NotNull PsiType type) {
    return suggestVariableName(VariableKind.PARAMETER, null, null, type, true);
  }


  public abstract SuggestedNameInfo suggestVariableName(@NotNull VariableKind kind,
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
  public abstract String variableNameToPropertyName(@NonNls String name, VariableKind variableKind);

  /**
   * Appends code style defined prefixes and/or suffixes for the specified variable kind
   * to the specified variable name.
   *
   * @param propertyName the base name of the variable.
   * @param variableKind the kind of the variable.
   * @return the variable name.
   */
  public abstract String propertyNameToVariableName(@NonNls String propertyName, VariableKind variableKind);

  /**
   * Suggests a unique name for the variable used at the specified location.
   *
   * @param baseName    the base name for the variable.
   * @param place       the location where the variable will be used.
   * @param lookForward if true, the existing variables are searched in both directions; if false - only backward
   * @return the generated unique name,
   */
  public abstract String suggestUniqueVariableName(@NonNls String baseName, PsiElement place, boolean lookForward);

  /**
   * Suggests a unique name for the variable used at the specified location.
   *
   * @param baseNameInfo    the base name info for the variable.
   * @param place       the location where the variable will be used.
   * @param lookForward if true, the existing variables are searched in both directions; if false - only backward
   * @return the generated unique name
   */
  @NotNull
  public SuggestedNameInfo suggestUniqueVariableName(@NotNull SuggestedNameInfo baseNameInfo,
                                                              PsiElement place,
                                                              boolean lookForward) {
    return suggestUniqueVariableName(baseNameInfo, place, false, lookForward);
  }

  /**
   * Suggests a unique name for the variable used at the specified location.
   *
   *
   * @param baseNameInfo    the base name info for the variable.
   * @param place           the location where the variable will be used.
   * @param ignorePlaceName  if true and place is PsiNamedElement, place.getName() would be still treated as unique name
   * @param lookForward     if true, the existing variables are searched in both directions; if false - only backward  @return the generated unique name,
   * @return the generated unique name
   */
  @NotNull public abstract SuggestedNameInfo suggestUniqueVariableName(@NotNull SuggestedNameInfo baseNameInfo,
                                                                       PsiElement place,
                                                                       boolean ignorePlaceName,
                                                                       boolean lookForward);

  /**
   * Replaces all references to Java classes in the contents of the specified element,
   * except for references to classes in the same package or in implicitly imported packages,
   * with full-qualified references.
   *
   * @param element the element to replace the references in.
   * @return the element in the PSI tree after the qualify operation corresponding to the
   *         original element.
   */
  public abstract PsiElement qualifyClassReferences(@NotNull PsiElement element);

  /**
   * Removes unused import statements from the specified Java file.
   *
   * @param file the file to remove the import statements from.
   * @throws IncorrectOperationException if the operation fails for some reason (for example,
   *                                     the file is read-only).
   */
  public abstract void removeRedundantImports(@NotNull PsiJavaFile file) throws IncorrectOperationException;

  @Nullable
  public abstract Collection<PsiImportStatementBase> findRedundantImports(PsiJavaFile file);
}
