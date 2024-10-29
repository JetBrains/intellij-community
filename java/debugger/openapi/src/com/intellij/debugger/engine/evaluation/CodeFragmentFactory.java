// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.engine.evaluation;

import com.intellij.debugger.engine.evaluation.expression.EvaluatorBuilder;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaCodeFragment;
import com.intellij.psi.PsiCodeFragment;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Allows to provide debugger editors support for non-java languages, for example in condition/log expression fields.
 */
public abstract class CodeFragmentFactory {
  public static final ExtensionPointName<CodeFragmentFactory> EXTENSION_POINT_NAME =
    ExtensionPointName.create("com.intellij.debugger.codeFragmentFactory");

  /**
   * @deprecated Use {@link CodeFragmentFactory#createPsiCodeFragment} instead
   */
  @Deprecated
  public JavaCodeFragment createCodeFragment(TextWithImports item, PsiElement context, Project project) {
    throw new AbstractMethodError();
  }

  /**
   * @deprecated Use {@link CodeFragmentFactory#createPresentationPsiCodeFragment(TextWithImports, PsiElement, Project)} instead
   */
  @Deprecated
  public JavaCodeFragment createPresentationCodeFragment(TextWithImports item, PsiElement context, Project project) {
    throw new AbstractMethodError();
  }

  public PsiCodeFragment createPsiCodeFragment(TextWithImports item, PsiElement context, Project project) {
    return createCodeFragment(item, context, project);
  }

  public PsiCodeFragment createPresentationPsiCodeFragment(TextWithImports item, PsiElement context, Project project) {
    return createPresentationCodeFragment(item, context, project);
  }

  public abstract boolean isContextAccepted(PsiElement contextElement);

  @NotNull
  public abstract LanguageFileType getFileType();

  /**
   * In case if createCodeFragment returns java code use
   * com.intellij.debugger.engine.evaluation.expression.EvaluatorBuilderImpl#getInstance()
   *
   * @return builder, which can evaluate expression for your code fragment
   */
  public abstract EvaluatorBuilder getEvaluatorBuilder();

  @ApiStatus.Internal
  public EvaluationContextWrapper createEvaluationContextWrapper() {
    return null;
  }
}
