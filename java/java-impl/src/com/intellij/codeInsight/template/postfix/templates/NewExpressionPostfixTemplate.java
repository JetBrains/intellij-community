// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.template.postfix.templates;

import com.intellij.codeInsight.completion.CompletionUtil;
import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.codeInsight.completion.JavaClassNameCompletionContributor;
import com.intellij.codeInsight.completion.JavaPsiClassReferenceElement;
import com.intellij.codeInsight.completion.OffsetMap;
import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.JavaResolveResult;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiResolveHelper;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.codeInsight.template.postfix.util.JavaPostfixTemplatesUtils.selectorAllExpressionsWithCurrentOffset;

public class NewExpressionPostfixTemplate extends StringBasedPostfixTemplate implements DumbAware {
  private static final Condition<PsiElement> CONSTRUCTOR = expression -> {
    PsiReferenceExpression ref = expression instanceof PsiMethodCallExpression call ? call.getMethodExpression() :
                                 expression instanceof PsiReferenceExpression r ? r :
                                 null;
    if (ref == null) return false;

    PsiExpression qualifier = ref.getQualifierExpression();

    return DumbService.getInstance(ref.getProject()).computeWithAlternativeResolveEnabled(() -> {
      JavaResolveResult result = ref.advancedResolve(true);
      PsiElement element = result.getElement();

      //todo implement proper support for Foo<Bar>, Foo.new Bar()
      if (qualifier != null && (!(qualifier instanceof PsiReferenceExpression) || element == null)) return false;

      if (element == null) return true;
      if (!(element instanceof PsiClass cls)) return false;
      PsiMethod[] constructors = cls.getConstructors();
      if (constructors.length == 0) return true;
      PsiResolveHelper helper = JavaPsiFacade.getInstance(element.getProject()).getResolveHelper();
      // Check whether there's at least one accessible constructor
      return !ContainerUtil.and(constructors, m -> !helper.isAccessible(m, ref, cls));
    });
  };

  protected NewExpressionPostfixTemplate() {
    super("new", "new T()", selectorAllExpressionsWithCurrentOffset(CONSTRUCTOR));
  }

  @Override
  public @Nullable String getTemplateString(@NotNull PsiElement element) {
    return element instanceof PsiMethodCallExpression ? "new $expr$" : "new $expr$($END$)";
  }


  @Override
  public boolean isApplicableForModCommand() {
    return true;
  }

  @Override
  public void expandForChooseExpression(@NotNull PsiElement expression, @NotNull Editor editor) {
    if (expression instanceof PsiReferenceExpression ref) {
      PsiClass psiClass = NewExpressionModExpandAction.resolveClass(ref);
      if (psiClass != null) {
        WriteAction.run(() -> insertConstructorCallWithSmartBraces(expression, editor, psiClass));
        return;
      }
    }
    super.expandForChooseExpression(expression, editor);
  }

  @ApiStatus.Experimental
  @Override
  public @NotNull PostfixModExpander createModExpander() {
    return createModExpander(new NewExpressionModExpandAction(new StringBasedModExpandAction(this)));
  }

  @Override
  protected PsiElement getElementToRemove(PsiElement expr) {
    return expr;
  }

  public void insertConstructorCallWithSmartBraces(@NotNull PsiElement expression,
                                                   @NotNull Editor editor,
                                                   @NotNull PsiClass javaClass) {
    Document document = editor.getDocument();
    PsiFile file = expression.getContainingFile();
    Project project = expression.getProject();

    SmartPsiElementPointer<PsiClass> pointer = SmartPointerManager.getInstance(project).createSmartPsiElementPointer(javaClass);
    int startOffset = NewExpressionModExpandAction.replaceExpressionTextByNewExpression(expression, document);
    PsiDocumentManager.getInstance(project).commitDocument(document);

    if (!javaClass.isValid()) {
      javaClass = pointer.getElement();
    }

    if (javaClass == null) return;

    JavaPsiClassReferenceElement item = JavaClassNameCompletionContributor.createClassLookupItem(javaClass, true);

    item.handleInsert(createInsertionContext(editor, file, item, startOffset));
  }

  private static @NotNull InsertionContext createInsertionContext(@NotNull Editor editor,
                                                                  @NotNull PsiFile file,
                                                                  @NotNull JavaPsiClassReferenceElement item,
                                                                  int startOffset) {
    Document document = editor.getDocument();
    final OffsetMap offsetMap = new OffsetMap(document);
    final InsertionContext insertionContext = new InsertionContext(offsetMap,
                                                                   Lookup.AUTO_INSERT_SELECT_CHAR,
                                                                   new LookupElement[]{item},
                                                                   file, editor, false);

    int offset = editor.getCaretModel().getOffset();
    return CompletionUtil.newContext(insertionContext, item, startOffset, offset);
  }
}
