// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.completion.util.MethodParenthesesHandler;
import com.intellij.codeInsight.hint.ParameterInfoControllerBase;
import com.intellij.codeInsight.hint.ShowParameterInfoContext;
import com.intellij.codeInsight.hint.api.impls.MethodParameterInfoHandler;
import com.intellij.codeInsight.hints.ParameterHintsPass;
import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.injected.editor.EditorWindow;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ThreeState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.codeInsight.completion.JavaMethodCallElement.getTypeParamsText;
import static com.intellij.codeInsight.completion.JavaMethodCallElement.startArgumentLiveTemplate;

public class JavaMethodCallInsertHandler<MethodCallElement extends JavaMethodCallElement> implements InsertHandler<MethodCallElement> {
  @Override
  public void handleInsert(@NotNull InsertionContext context, @NotNull MethodCallElement item) {
    Document document = context.getDocument();
    PsiFile file = context.getFile();
    PsiMethod method = item.getObject();

    LookupElement[] allItems = context.getElements();
    ThreeState hasParams = method.getParameterList().isEmpty() ? ThreeState.NO
                                                               : MethodParenthesesHandler.overloadsHaveParameters(allItems, method);
    if (method.isConstructor()) {
      PsiClass aClass = method.getContainingClass();
      if (aClass != null && aClass.getTypeParameters().length > 0) {
        document.insertString(context.getTailOffset(), "<>");
      }
    }
    JavaCompletionUtil.insertParentheses(context, item, false, hasParams, false);

    int offset = context.getStartOffset();
    OffsetKey refStart = context.trackOffset(offset, true);
    beforeHandle(context);
    if (item.isNeedExplicitTypeParameters()) {
      qualifyMethodCall(item, file, context.getOffset(refStart), document);
      insertExplicitTypeParameters(item, context, refStart);
    }
    else if (item.getHelper() != null) {
      context.commitDocument();
      importOrQualify(item, document, file, method, context.getOffset(refStart));
    }

    PsiCallExpression methodCall = findCallAtOffset(context, context.getOffset(refStart));
    // make sure this is the method call we've just added, not the enclosing one
    if (methodCall != null) {
      PsiElement completedElement = methodCall instanceof PsiMethodCallExpression ?
                                    ((PsiMethodCallExpression)methodCall).getMethodExpression().getReferenceNameElement() : null;
      TextRange completedElementRange = completedElement == null ? null : completedElement.getTextRange();
      if (completedElementRange == null || completedElementRange.getStartOffset() != context.getOffset(refStart)) {
        methodCall = null;
      }
    }
    if (methodCall != null) {
      CompletionMemory.registerChosenMethod(method, methodCall);
      handleNegation(context, document, methodCall, item.isNegatable());
    }

    afterHandle(context, methodCall);

    if (canStartArgumentLiveTemplate()) {
      startArgumentLiveTemplate(context, method);
    }
    showParameterHints(item, context, method, methodCall);
  }

  /**
   * Called before insertion methods. Performs any necessary pre-processing or setup.
   *
   * @param context the insertion context for the code template, must not be null
   */
  protected void beforeHandle(@NotNull InsertionContext context) {
  }

  /**
   * Called after insertion methods. Performs any necessary post-processing or cleanup.
   *
   * @param context the insertion context for the code template
   * @param call    the PsiCallExpression representing the inserted code, or null if no code was inserted
   */
  protected void afterHandle(@NotNull InsertionContext context, @Nullable PsiCallExpression call) {

  }

  /**
   * Checks if the argument live template can be started.
   * see registry key java.completion.argument.live.template.description.
   * This option allows to prevent running templates if this key is enabled
   *
   * @return true if the argument live template can be started, otherwise false.
   */
  protected boolean canStartArgumentLiveTemplate() {
    return true;
  }

  private static void handleNegation(@NotNull InsertionContext context,
                                     @NotNull Document document,
                                     @NotNull PsiCallExpression methodCall,
                                     boolean negatable) {
    if (context.getCompletionChar() == '!' && negatable) {
      context.setAddCompletionChar(false);
      FeatureUsageTracker.getInstance().triggerFeatureUsed(CodeCompletionFeatures.EXCLAMATION_FINISH);
      document.insertString(methodCall.getTextRange().getStartOffset(), "!");
    }
  }

  private void importOrQualify(@NotNull MethodCallElement item,
                               @NotNull Document document,
                               @NotNull PsiFile file,
                               @NotNull PsiMethod method,
                               int startOffset) {
    if (!needImportOrQualify()) {
      return;
    }
    if (item.willBeImported()) {
      PsiClass containingClass = item.getContainingClass();
      if (method.isConstructor()) {
        PsiNewExpression newExpression = PsiTreeUtil.findElementOfClassAtOffset(file, startOffset, PsiNewExpression.class, false);
        if (newExpression != null) {
          PsiJavaCodeReferenceElement ref = newExpression.getClassReference();
          if (ref != null && containingClass != null && !ref.isReferenceTo(containingClass)) {
            ref.bindToElement(containingClass);
            return;
          }
        }
      }
      else {
        PsiReferenceExpression ref = PsiTreeUtil.findElementOfClassAtOffset(file, startOffset, PsiReferenceExpression.class, false);
        if (ref != null && containingClass != null && !ref.isReferenceTo(method)) {
          ref.bindToElementViaStaticImport(containingClass);
        }
        return;
      }
    }

    qualifyMethodCall(item, file, startOffset, document);
  }

  /**
   * Determines if import or qualification is needed.
   *
   * @return true if import or qualification is needed, false otherwise
   */
  protected boolean needImportOrQualify() {
    return true;
  }

  private void qualifyMethodCall(@NotNull MethodCallElement item, @NotNull PsiFile file, int startOffset, @NotNull Document document) {
    PsiReference reference = file.findReferenceAt(startOffset);
    if (reference instanceof PsiReferenceExpression && ((PsiReferenceExpression)reference).isQualified()) {
      return;
    }

    PsiMethod method = item.getObject();
    if (method.isConstructor()) return;
    if (!method.hasModifierProperty(PsiModifier.STATIC)) {
      document.insertString(startOffset, "this.");
      return;
    }

    PsiClass containingClass = item.getContainingClass();
    if (containingClass == null) return;

    document.insertString(startOffset, ".");
    JavaCompletionUtil.insertClassReference(containingClass, file, startOffset);
  }

  private void insertExplicitTypeParameters(@NotNull MethodCallElement item,
                                            @NotNull InsertionContext context,
                                            @NotNull OffsetKey refStart) {
    context.commitDocument();

    String typeParams = getTypeParamsText(false, item.getObject(), item.getInferenceSubstitutor());
    if (typeParams != null) {
      context.getDocument().insertString(context.getOffset(refStart), typeParams);
      JavaCompletionUtil.shortenReference(context.getFile(), context.getOffset(refStart));
    }
  }

  static PsiCallExpression findCallAtOffset(@NotNull InsertionContext context, int offset) {
    context.commitDocument();
    return PsiTreeUtil.findElementOfClassAtOffset(context.getFile(), offset, PsiCallExpression.class, false);
  }

  public static void showParameterHints(@NotNull LookupElement element,
                                        @NotNull InsertionContext context,
                                        @NotNull PsiMethod method,
                                        @Nullable PsiCallExpression methodCall) {
    if (!CodeInsightSettings.getInstance().SHOW_PARAMETER_NAME_HINTS_ON_COMPLETION ||
        context.getCompletionChar() == Lookup.COMPLETE_STATEMENT_SELECT_CHAR ||
        context.getCompletionChar() == Lookup.REPLACE_SELECT_CHAR ||
        methodCall == null ||
        methodCall.getContainingFile() instanceof PsiCodeFragment ||
        element.getUserData(JavaMethodMergingContributor.MERGED_ELEMENT) != null) {
      return;
    }
    PsiParameterList parameterList = method.getParameterList();
    int parametersCount = parameterList.getParametersCount();
    PsiExpressionList parameterOwner = methodCall.getArgumentList();
    if (parameterOwner == null || !"()".equals(parameterOwner.getText()) || parametersCount == 0) {
      return;
    }

    Editor editor = context.getEditor();
    if (editor instanceof EditorWindow) return;

    Project project = context.getProject();
    Document document = editor.getDocument();
    PsiDocumentManager.getInstance(project).doPostponedOperationsAndUnblockDocument(document);

    int limit = JavaMethodCallElement.getCompletionHintsLimit();

    CaretModel caretModel = editor.getCaretModel();
    int offset = caretModel.getOffset();

    int afterParenOffset = offset + 1;
    if (afterParenOffset < document.getTextLength() &&
        Character.isJavaIdentifierPart(document.getImmutableCharSequence().charAt(afterParenOffset))) {
      return;
    }

    int braceOffset = offset - 1;
    int numberOfParametersToDisplay = parametersCount > 1 && PsiImplUtil.isVarArgs(method) ? parametersCount - 1 : parametersCount;
    int numberOfCommas = Math.min(numberOfParametersToDisplay, limit) - 1;
    String commas = Registry.is("editor.completion.hints.virtual.comma") ? "" : StringUtil.repeat(", ", numberOfCommas);
    document.insertString(offset, commas);

    PsiDocumentManager.getInstance(project).commitDocument(document);
    MethodParameterInfoHandler handler = new MethodParameterInfoHandler();
    ShowParameterInfoContext infoContext = new ShowParameterInfoContext(editor, project, context.getFile(), offset, braceOffset);
    if (!methodCall.isValid() || handler.findElementForParameterInfo(infoContext) == null) {
      document.deleteString(offset, offset + commas.length());
      return;
    }

    JavaMethodCallElement.setCompletionMode(methodCall, true);
    context.setLaterRunnable(() -> {
      Object[] itemsToShow = infoContext.getItemsToShow();
      PsiExpressionList methodCallArgumentList = methodCall.getArgumentList();
      ParameterInfoControllerBase controller =
        ParameterInfoControllerBase.createParameterInfoController(project, editor, braceOffset, itemsToShow, null,
                                                                  methodCallArgumentList, handler, false, false);
      Disposable hintsDisposal = () -> JavaMethodCallElement.setCompletionMode(methodCall, false);
      if (Disposer.isDisposed(controller)) {
        Disposer.dispose(hintsDisposal);
        document.deleteString(offset, offset + commas.length());
      }
      else {
        ParameterHintsPass.asyncUpdate(methodCall, editor);
        Disposer.register(controller, hintsDisposal);
      }
    });
  }
}
