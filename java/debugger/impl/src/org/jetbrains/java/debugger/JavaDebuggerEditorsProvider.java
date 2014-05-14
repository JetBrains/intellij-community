package org.jetbrains.java.debugger;

import com.intellij.debugger.engine.evaluation.CodeFragmentFactory;
import com.intellij.debugger.engine.evaluation.TextWithImports;
import com.intellij.debugger.engine.evaluation.TextWithImportsImpl;
import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.debugger.ui.DebuggerEditorImpl;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.lang.Language;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

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
  public Collection<Language> getSupportedLanguages(@NotNull Project project, @Nullable XSourcePosition sourcePosition) {
    if (sourcePosition != null) {
      PsiElement context = getContextElement(sourcePosition.getFile(), sourcePosition.getOffset(), project);
      Collection<Language> res = new ArrayList<Language>();
      for (CodeFragmentFactory factory : DebuggerUtilsEx.getCodeFragmentFactories(context)) {
        res.add(factory.getFileType().getLanguage());
      }
      return res;
    }
    return Collections.emptyList();
  }

  @NotNull
  @Override
  public XExpression createExpression(@NotNull Project project, @NotNull Document document, @Nullable Language language, @NotNull EvaluationMode mode) {
    PsiDocumentManager.getInstance(project).commitDocument(document);
    PsiFile psiFile = PsiDocumentManager.getInstance(project).getPsiFile(document);
    if (psiFile != null) {
      return new XExpressionImpl(psiFile.getText(), language, ((JavaCodeFragment)psiFile).importsToString(), mode);
    }
    return super.createExpression(project, document, language, mode);
  }

  @Override
  protected PsiFile createExpressionCodeFragment(@NotNull Project project,
                                                 @NotNull XExpression expression,
                                                 @Nullable PsiElement context,
                                                 boolean isPhysical) {
    TextWithImports text = TextWithImportsImpl.fromXExpression(expression);
    if (text != null && context != null) {
      CodeFragmentFactory factory = DebuggerEditorImpl.findAppropriateFactory(text, context);
      JavaCodeFragment codeFragment = factory.createPresentationCodeFragment(text, context, project);
      codeFragment.forceResolveScope(GlobalSearchScope.allScope(project));
      if (context != null) {
        final PsiClass contextClass = PsiTreeUtil.getNonStrictParentOfType(context, PsiClass.class);
        if (contextClass != null) {
          final PsiClassType contextType =
            JavaPsiFacade.getInstance(codeFragment.getProject()).getElementFactory().createType(contextClass);
          codeFragment.setThisType(contextType);
        }
      }
      return codeFragment;
    }
    else {
      return super.createExpressionCodeFragment(project, expression, context, isPhysical);
    }
  }
}
