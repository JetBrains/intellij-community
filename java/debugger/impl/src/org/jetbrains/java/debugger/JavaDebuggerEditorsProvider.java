/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package org.jetbrains.java.debugger;

import com.intellij.debugger.engine.evaluation.CodeFragmentFactory;
import com.intellij.debugger.engine.evaluation.TextWithImports;
import com.intellij.debugger.engine.evaluation.TextWithImportsImpl;
import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.lang.Language;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
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
  public Collection<Language> getSupportedLanguages(@NotNull Project project, @Nullable XSourcePosition sourcePosition) {
    if (sourcePosition != null) {
      PsiElement context = getContextElement(sourcePosition.getFile(), sourcePosition.getOffset(), project);
      return DebuggerUtilsEx.getCodeFragmentFactories(context).stream()
        .map(factory -> factory.getFileType().getLanguage())
        .collect(Collectors.toList());
    }
    return Collections.emptyList();
  }

  @NotNull
  @Override
  public XExpression createExpression(@NotNull Project project, @NotNull Document document, @Nullable Language language, @NotNull EvaluationMode mode) {
    PsiFile psiFile = PsiDocumentManager.getInstance(project).getPsiFile(document);
    if (psiFile != null) {
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
      codeFragment.forceResolveScope(GlobalSearchScope.allScope(project));

      final PsiClass contextClass = PsiTreeUtil.getNonStrictParentOfType(context, PsiClass.class);
      if (contextClass != null) {
        final PsiClassType contextType =
          JavaPsiFacade.getInstance(codeFragment.getProject()).getElementFactory().createType(contextClass);
        codeFragment.setThisType(contextType);
      }
      return codeFragment;
    }
    else {
      return super.createExpressionCodeFragment(project, expression, context, isPhysical);
    }
  }
}
