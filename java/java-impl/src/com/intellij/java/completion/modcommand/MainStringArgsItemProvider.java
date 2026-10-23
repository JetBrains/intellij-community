// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.completion.modcommand;

import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcompletion.CommonCompletionItem;
import com.intellij.modcompletion.ModCompletionItemPresentation;
import com.intellij.modcompletion.ModCompletionResult;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.MarkupText;
import com.intellij.patterns.PsiJavaElementPattern;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameterList;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiResolveHelper;
import com.intellij.psi.PsiTypeElement;
import com.intellij.psi.PsiTypes;
import com.intellij.psi.PsiVariable;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.util.PsiMethodUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.HardcodedMethodConstants;
import org.jetbrains.annotations.NotNullByDefault;

import static com.intellij.patterns.PsiJavaPatterns.psiElement;

/**
 * Completes the {@code String[] args} parameter of a {@code main} method, and the {@code args} variable inside a {@code main} method
 * that has no parameters. The second item also adds the parameter to the method.
 */
@NotNullByDefault
final class MainStringArgsItemProvider extends JavaModCompletionItemProvider implements DumbAware {
  @NlsSafe private static final String ARGS = "args";
  @NlsSafe private static final String ARGS_PARAMETER = "String[] args";

  private static final PsiJavaElementPattern.Capture<PsiElement> AFTER_DOT_FILTER =
    psiElement().andNot(
      psiElement().afterLeaf(psiElement(JavaTokenType.DOT).withParent(PsiErrorElement.class))
    );

  @Override
  public void provideItems(CompletionContext context, ModCompletionResult sink) {
    CompletionType completionType = context.getCompletionType();
    if (completionType != CompletionType.BASIC && completionType != CompletionType.SMART) return;
    if (!(context.getOriginalFile().getFileType() instanceof JavaFileType) || !AFTER_DOT_FILTER.accepts(context.getPosition())) {
      return;
    }
    if (!(context.getPosition() instanceof PsiIdentifier identifier)) return;
    tryAddArgsParameter(identifier, sink);
    tryAddArgsVariable(identifier, sink);
  }

  private static void tryAddArgsVariable(PsiIdentifier identifier, ModCompletionResult sink) {
    if (!(identifier.getParent() instanceof PsiReferenceExpression referenceExpression)) return;
    if (referenceExpression.getQualifierExpression() != null) return;
    PsiMethod method = PsiTreeUtil.getParentOfType(referenceExpression, PsiMethod.class, false);
    if (method == null) return;
    if (!HardcodedMethodConstants.MAIN.equals(method.getName())) return;
    if (method.getParameterList().getParametersCount() != 0) return;
    if (!PsiMethodUtil.isMainMethod(method)) return;
    PsiVariable args = PsiResolveHelper.getInstance(method.getProject()).resolveReferencedVariable(ARGS, referenceExpression);
    if (args != null) return;
    sink.accept(new CommonCompletionItem(ARGS)
                  .withPresentation(new ModCompletionItemPresentation(MarkupText.plainText(ARGS).highlightAll(MarkupText.Kind.STRONG))
                                      .withDetailText(MarkupText.plainText(ARGS_PARAMETER)))
                  .withAdditionalUpdater(MainStringArgsItemProvider::addArgsParameter));
  }

  private static void tryAddArgsParameter(PsiIdentifier identifier, ModCompletionResult sink) {
    if (!(identifier.getParent() instanceof PsiJavaCodeReferenceElement referenceElement)) return;
    PsiElement[] referenceChildren = referenceElement.getChildren();
    if (referenceChildren.length != 2 || referenceChildren[0] != identifier) return;
    if (!(referenceElement.getParent() instanceof PsiTypeElement typeElement)) return;
    if (typeElement.getChildren().length != 1) return;
    if (!(typeElement.getParent() instanceof PsiParameterList parameterList)) return;
    if (parameterList.getParameters().length > 0) return;
    if (!(parameterList.getParent() instanceof PsiMethod method)) return;
    if (!HardcodedMethodConstants.MAIN.equals(method.getName())) return;
    if (!PsiTypes.voidType().equals(method.getReturnType())) return;
    sink.accept(new CommonCompletionItem(ARGS_PARAMETER)
                  .withPresentation(MarkupText.plainText(ARGS_PARAMETER).highlightAll(MarkupText.Kind.STRONG))
                  .addLookupString(ARGS)
                  .withPriority(100));
  }

  /** Adds {@code String[] args} to the empty parameter list of the {@code main} method that holds {@code completionStart}. */
  private static void addArgsParameter(int completionStart, ModPsiUpdater updater) {
    PsiElement element = updater.getPsiFile().findElementAt(completionStart);
    PsiMethod method = PsiTreeUtil.getParentOfType(element, PsiMethod.class, false);
    if (method == null) return;
    PsiParameterList parameterList = method.getParameterList();
    if (parameterList.getParametersCount() != 0) return;
    PsiElement lParenth = parameterList.getFirstChild();
    if (lParenth == null || lParenth.getNode().getElementType() != JavaTokenType.LPARENTH) return;
    Document document = updater.getDocument();
    document.insertString(lParenth.getTextRange().getEndOffset(), "String[]args");
    PsiDocumentManager.getInstance(updater.getProject()).commitDocument(document);
    CodeStyleManager.getInstance(updater.getProject()).reformat(method.getParameterList());
  }

  @Override
  public boolean isEnabled() {
    return true;
  }
}
