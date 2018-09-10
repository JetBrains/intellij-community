// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.java.debugger;

import com.intellij.debugger.engine.evaluation.CodeFragmentFactory;
import com.intellij.debugger.engine.evaluation.TextWithImports;
import com.intellij.debugger.engine.evaluation.TextWithImportsImpl;
import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.debugger.impl.DebuggerUtilsImpl;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.lang.Language;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.xdebugger.XExpression;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.evaluation.EvaluationMode;
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProviderBase;
import com.intellij.xdebugger.impl.breakpoints.XExpressionImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.stream.Collectors;

public class JavaDebuggerEditorsProvider extends XDebuggerEditorsProviderBase {
  @NotNull
  @Override
  public FileType getFileType() {
    return JavaFileType.INSTANCE;
  }

  @Override
  protected PsiFile createExpressionCodeFragment(@NotNull Project project,
                                                 @NotNull String text,
                                                 @Nullable PsiElement context,
                                                 boolean isPhysical) {
    return JavaCodeFragmentFactory.getInstance(project).createExpressionCodeFragment(text, context, null, isPhysical);
  }

  @NotNull
  @Override
  public Collection<Language> getSupportedLanguages(@Nullable PsiElement context) {
    return DebuggerUtilsEx.getCodeFragmentFactories(context).stream()
      .map(factory -> factory.getFileType().getLanguage())
      .collect(Collectors.toList());
  }

  @NotNull
  @Override
  public Collection<Language> getSupportedLanguages(@NotNull Project project, @Nullable XSourcePosition sourcePosition) {
    if (sourcePosition != null) {
      return getSupportedLanguages(getContextElement(sourcePosition.getFile(), sourcePosition.getOffset(), project));
    }
    return Collections.emptyList();
  }

  @NotNull
  @Override
  public XExpression createExpression(@NotNull Project project, @NotNull Document document, @Nullable Language language, @NotNull EvaluationMode mode) {
    PsiFile psiFile = PsiDocumentManager.getInstance(project).getPsiFile(document);
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
      JavaCodeFragment codeFragment = factory.createPresentationCodeFragment(text, context, project);

      if (context != null) {
        PsiType contextType = context.getUserData(DebuggerUtilsImpl.PSI_TYPE_KEY);
        if (contextType == null) {
          PsiClass contextClass = PsiTreeUtil.getNonStrictParentOfType(context, PsiClass.class);
          if (contextClass != null) {
            contextType = JavaPsiFacade.getInstance(codeFragment.getProject()).getElementFactory().createType(contextClass);
          }
        }
        codeFragment.setThisType(contextType);
      }

      return codeFragment;
    }
    else {
      return super.createExpressionCodeFragment(project, expression, context, isPhysical);
    }
  }
}
