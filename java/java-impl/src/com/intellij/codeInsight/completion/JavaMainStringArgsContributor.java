// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.DumbAware;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.util.PsiMethodUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ProcessingContext;
import com.siyeh.HardcodedMethodConstants;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import static com.intellij.patterns.PlatformPatterns.psiElement;

@ApiStatus.Experimental
public final class JavaMainStringArgsContributor extends CompletionContributor implements DumbAware {
  public JavaMainStringArgsContributor() {
    extend(CompletionType.BASIC, psiElement(), new CompletionProvider<>() {
      @Override
      protected void addCompletions(@NotNull CompletionParameters parameters,
                                    @NotNull ProcessingContext context,
                                    @NotNull CompletionResultSet result) {
        FileType fileType = parameters.getOriginalFile().getFileType();
        if (!(fileType instanceof JavaFileType)) {
          return;
        }
        tryAddArgsParameter(parameters, result);
        tryAddArgsVariable(parameters, result);
      }
    });
  }

  private static void tryAddArgsVariable(@NotNull CompletionParameters parameters, @NotNull CompletionResultSet result) {
    PsiElement position = parameters.getPosition();
    if (!(position instanceof PsiIdentifier psiIdentifier)) return;
    if (!(psiIdentifier.getParent() instanceof PsiReferenceExpression referenceExpression)) return;
    PsiMethod method = PsiTreeUtil.getParentOfType(referenceExpression, PsiMethod.class, false);
    if (method == null) return;
    if (!HardcodedMethodConstants.MAIN.equals(method.getName())) return;
    PsiParameterList parameterList = method.getParameterList();
    if (parameterList.getParametersCount() != 0) return;
    if (!PsiMethodUtil.isMainMethod(method)) return;
    PsiVariable args = PsiResolveHelper.getInstance(method.getProject()).resolveReferencedVariable("args", referenceExpression);
    if (args != null) return;
    result.addElement(LookupElementBuilder.create("args")
                        .bold()
                        .withTypeText("String[] args")
                        .withInsertHandler(new ArgsInsertHandler()));
  }

  private static void tryAddArgsParameter(@NotNull CompletionParameters parameters, @NotNull CompletionResultSet result) {
    PsiElement position = parameters.getPosition();
    if (!(position instanceof PsiIdentifier psiIdentifier)) return;
    if (!(psiIdentifier.getParent() instanceof PsiJavaCodeReferenceElement referenceElement)) return;
    if (referenceElement.getChildren().length != 2 ||
        referenceElement.getChildren()[0] != psiIdentifier) {
      return;
    }
    if (!(referenceElement.getParent() instanceof PsiTypeElement typeElement)) return;
    if (typeElement.getChildren().length != 1) return;
    if (!(typeElement.getParent() instanceof PsiParameterList parameterList)) return;
    if (parameterList.getParameters().length > 0) return;
    if (!(parameterList.getParent() instanceof PsiMethod method)) return;
    if (!HardcodedMethodConstants.MAIN.equals(method.getName())) return;
    if (!PsiTypes.voidType().equals(method.getReturnType())) return;
    result.addElement(
      PrioritizedLookupElement.withPriority(
        LookupElementBuilder.create("String[] args")
          .bold()
          .withLookupString("args"), 100.));
  }

  private static class ArgsInsertHandler implements InsertHandler<LookupElement> {
    @Override
    public void handleInsert(@NotNull InsertionContext context, @NotNull LookupElement item) {
      Document document = context.getDocument();
      PsiFile psiFile = context.getFile();
      int offset = context.getStartOffset();
      PsiElement element = psiFile.findElementAt(offset);
      PsiMethod currentMethod = PsiTreeUtil.getParentOfType(element, PsiMethod.class, false);
      if (currentMethod == null) return;
      PsiParameterList parameterList = currentMethod.getParameterList();
      if (parameterList.getParametersCount() != 0) return;
      int offsetForArgs = -1;
      for (PsiElement child : parameterList.getChildren()) {
        if (child.getNode().getElementType() == JavaTokenType.LPARENTH) {
          offsetForArgs = child.getTextRange().getEndOffset();
          break;
        }
      }
      if (offsetForArgs == -1) return;
      document.insertString(offsetForArgs, "String[]args");
      PsiDocumentManager.getInstance(psiFile.getProject()).commitDocument(document);
      CodeStyleManager.getInstance(context.getProject()).reformat(currentMethod.getParameterList());
    }
  }
}
