// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.java.debugger;

import com.intellij.debugger.engine.evaluation.CodeFragmentFactory;
import com.intellij.debugger.engine.evaluation.TextWithImports;
import com.intellij.debugger.engine.evaluation.TextWithImportsImpl;
import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.lang.Language;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.xdebugger.XExpression;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.evaluation.EvaluationMode;
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProviderBase;
import com.intellij.xdebugger.impl.breakpoints.XExpressionImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collection;
import java.util.Collections;

public class JavaDebuggerEditorsProvider extends XDebuggerEditorsProviderBase {
  @Override
  public @NotNull FileType getFileType() {
    return JavaFileType.INSTANCE;
  }

  @Override
  protected PsiFile createExpressionCodeFragment(@NotNull Project project,
                                                 @NotNull String text,
                                                 @Nullable PsiElement context,
                                                 boolean isPhysical) {
    return JavaCodeFragmentFactory.getInstance(project).createExpressionCodeFragment(text, context, null, isPhysical);
  }

  @Override
  public @NotNull @Unmodifiable Collection<Language> getSupportedLanguages(@Nullable PsiElement context) {
    return ContainerUtil.map(DebuggerUtilsEx.getCodeFragmentFactories(context), factory -> factory.getFileType().getLanguage());
  }

  @Override
  public @NotNull @Unmodifiable Collection<Language> getSupportedLanguages(@NotNull Project project, @Nullable XSourcePosition sourcePosition) {
    if (sourcePosition != null) {
      return getSupportedLanguages(getContextElement(sourcePosition.getFile(), sourcePosition.getOffset(), project));
    }
    return Collections.emptyList();
  }

  @Override
  public @NotNull XExpression createExpression(@NotNull Project project, @NotNull Document document, @Nullable Language language, @NotNull EvaluationMode mode) {
    PsiFile psiFile = ReadAction.compute(() -> PsiDocumentManager.getInstance(project).getPsiFile(document));
    if (psiFile instanceof JavaCodeFragment) {
      return new XExpressionImpl(document.getText(), language, StringUtil.nullize(((JavaCodeFragment)psiFile).importsToString()), mode);
    }
    return super.createExpression(project, document, language, mode);
  }

  @Override
  protected PsiFile createExpressionCodeFragment(@NotNull Project project,
                                                 @NotNull XExpression expression,
                                                 @Nullable PsiElement context,
                                                 boolean isPhysical) {
    TextWithImports text = TextWithImportsImpl.fromXExpression(expression);
    if (text != null) {
      CodeFragmentFactory factory = DebuggerUtilsEx.findAppropriateCodeFragmentFactory(text, context);
      return factory.createPresentationPsiCodeFragment(text, context, project);
    }
    else {
      return super.createExpressionCodeFragment(project, expression, context, isPhysical);
    }
  }
}
