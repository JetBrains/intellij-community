/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.psi;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class JavaCodeFragmentFactory {
  public static JavaCodeFragmentFactory getInstance(Project project) {
    return ServiceManager.getService(project, JavaCodeFragmentFactory.class);
  }
  
  /**
   * Creates a Java expression code fragment from the text of the expression.
   *
   * @param text         the text of the expression to create.
   * @param context      the context for resolving references from the code fragment.
   * @param expectedType expected type of the expression (does not have any effect on creation
   *                     but can be accessed as {@link PsiExpressionCodeFragment#getExpectedType()}).
   * @param isPhysical   whether the code fragment is created as a physical element
   *                     (see {@link PsiElement#isPhysical()}).
   * @return the created code fragment.
   */
  @NotNull
  public abstract PsiExpressionCodeFragment createExpressionCodeFragment(@NotNull String text,
                                                                         @Nullable PsiElement context,
                                                                         @Nullable final PsiType expectedType,
                                                                         boolean isPhysical);

  /**
   * Creates a Java code fragment from the text of a Java code block.
   *
   * @param text       the text of the code block to create.
   * @param context    the context for resolving references from the code fragment.
   * @param isPhysical whether the code fragment is created as a physical element
   *                   (see {@link PsiElement#isPhysical()}).
   * @return the created code fragment.
   */
  @NotNull
  public abstract JavaCodeFragment createCodeBlockCodeFragment(@NotNull String text, @Nullable PsiElement context, boolean isPhysical);

  /**
   * Flag for {@linkplain #createTypeCodeFragment(String, PsiElement, boolean, int)} - allows void type.
   */
  public static final int ALLOW_VOID = 0x01;
  /**
   * Flag for {@linkplain #createTypeCodeFragment(String, PsiElement, boolean, int)} - allows type with ellipsis.
   */
  public static final int ALLOW_ELLIPSIS = 0x02;
  /**
   * Flag for {@linkplain #createTypeCodeFragment(String, PsiElement, boolean, int)} - allows disjunctive type.
   */
  public static final int ALLOW_DISJUNCTION = 0x04;
  /**
   * Flag for {@linkplain #createTypeCodeFragment(String, PsiElement, boolean, int)} - allows conjunctive type.
   */
  public static final int ALLOW_INTERSECTION = 0x08;

  /**
   * Creates a Java type code fragment from the text of the name of a Java type (the name
   * of a primitive type, array type or class), with <code>void</code> and ellipsis
   * not treated as a valid type.
   *
   * @param text       the text of the Java type to create.
   * @param context    the context for resolving references from the code fragment.
   * @param isPhysical whether the code fragment is created as a physical element
   *                   (see {@link PsiElement#isPhysical()}).
   * @return the created code fragment.
   */
  @NotNull
  public abstract PsiTypeCodeFragment createTypeCodeFragment(@NotNull String text, @Nullable PsiElement context, boolean isPhysical);

  /**
   * Creates a Java type code fragment from the text of the name of a Java type (the name
   * of a primitive type, array type or class).<br>
   * {@code void}, ellipsis and disjunctive types are optionally treated as valid ones.
   *
   * @param text       the text of the Java type to create.
   * @param context    the context for resolving references from the code fragment.
   * @param isPhysical whether the code fragment is created as a physical element
   *                   (see {@link PsiElement#isPhysical()}).
   * @param flags      types allowed to present in text.
   * @return the created code fragment.
   */
  @NotNull
  public abstract PsiTypeCodeFragment createTypeCodeFragment(@NotNull String text,
                                                             @Nullable PsiElement context,
                                                             boolean isPhysical,
                                                             @MagicConstant(flags = {ALLOW_VOID, ALLOW_ELLIPSIS, ALLOW_DISJUNCTION, ALLOW_INTERSECTION}) int flags);

  /**
   * Creates a Java reference code fragment from the text of a Java reference to a
   * package or class.
   *
   * @param text              the text of the reference to create.
   * @param context           the context for resolving the reference.
   * @param isPhysical        whether the code fragment is created as a physical element
   *                          (see {@link PsiElement#isPhysical()}).
   * @param isClassesAccepted if true then classes as well as packages are accepted as
   *                          reference target, otherwise only packages are
   * @return the created reference fragment.
   */
  @NotNull
  public abstract PsiJavaCodeReferenceCodeFragment createReferenceCodeFragment(@NotNull String text,
                                                                               @Nullable PsiElement context,
                                                                               boolean isPhysical,
                                                                               boolean isClassesAccepted);

}
