// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.AutoPopupController;
import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.completion.util.MethodParenthesesHandler;
import com.intellij.codeInsight.hint.ParameterInfoControllerBase;
import com.intellij.codeInsight.hint.ShowParameterInfoContext;
import com.intellij.codeInsight.hint.api.impls.MethodParameterInfoHandler;
import com.intellij.codeInsight.hints.ParameterHintsPass;
import com.intellij.codeInsight.lookup.*;
import com.intellij.codeInsight.lookup.impl.JavaElementLookupRenderer;
import com.intellij.codeInsight.template.*;
import com.intellij.codeInsight.template.impl.ConstantNode;
import com.intellij.codeInsight.template.impl.TemplateImpl;
import com.intellij.codeInsight.template.impl.TemplateState;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.injected.editor.EditorWindow;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ClassConditionKey;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.ThreeState;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

public class JavaMethodCallElement extends LookupItem<PsiMethod> implements TypedLookupItem, StaticallyImportable {
  public static final ClassConditionKey<JavaMethodCallElement> CLASS_CONDITION_KEY = ClassConditionKey.create(JavaMethodCallElement.class);
  public static final Key<Boolean> COMPLETION_HINTS = Key.create("completion.hints");
  @Nullable private final PsiClass myContainingClass;
  private final PsiMethod myMethod;
  private final MemberLookupHelper myHelper;
  private final boolean myNegatable;
  private PsiSubstitutor myQualifierSubstitutor = PsiSubstitutor.EMPTY;
  private PsiSubstitutor myInferenceSubstitutor = PsiSubstitutor.EMPTY;
  private boolean myNeedExplicitTypeParameters;
  private String myForcedQualifier = "";
  @Nullable private String myPresentableTypeArgs;

  public JavaMethodCallElement(@NotNull PsiMethod method) {
    this(method, null);
  }

  private JavaMethodCallElement(PsiMethod method, @Nullable MemberLookupHelper helper) {
    super(method, method.isConstructor() ? "new " + method.getName() : method.getName());
    myMethod = method;
    myContainingClass = method.getContainingClass();
    myHelper = helper;
    PsiType type = method.getReturnType();
    myNegatable = type != null && PsiTypes.booleanType().isAssignableFrom(type);
  }

  public JavaMethodCallElement(PsiMethod method, boolean shouldImportStatic, boolean mergedOverloads) {
    this(method, new MemberLookupHelper(method, method.getContainingClass(), shouldImportStatic || method.isConstructor(), mergedOverloads));
    if (!shouldImportStatic && !method.isConstructor()) {
      if (myContainingClass != null) {
        String className = myContainingClass.getName();
        if (className != null) {
          addLookupStrings(className + "." + myMethod.getName());
        }
      }
    }
  }

  boolean isNegatable() {
    return myNegatable;
  }

  void setForcedQualifier(@NotNull String forcedQualifier) {
    myForcedQualifier = forcedQualifier;
    setLookupString(forcedQualifier + getLookupString());
  }

  @Override
  public PsiType getType() {
    PsiType type = MemberLookupHelper.getDeclaredType(getObject(), getInferenceSubstitutor());
    return getSubstitutor().substitute(type);
  }

  public void setInferenceSubstitutorFromExpectedType(@NotNull PsiElement place, @NotNull PsiType expectedType) {
    if (myMethod.isConstructor()) {
      if (expectedType instanceof PsiClassType) {
        PsiClassType genericType = GenericsUtil.getExpectedGenericType(place, myContainingClass, (PsiClassType)expectedType);
        myQualifierSubstitutor = myInferenceSubstitutor = genericType.resolveGenerics().getSubstitutor();
      } else {
        myQualifierSubstitutor = myInferenceSubstitutor = PsiSubstitutor.EMPTY;
      }
      myNeedExplicitTypeParameters = false;
    } else {
      myInferenceSubstitutor = SmartCompletionDecorator.calculateMethodReturnTypeSubstitutor(myMethod, expectedType);
      myNeedExplicitTypeParameters = mayNeedTypeParameters(place) && SmartCompletionDecorator.hasUnboundTypeParams(myMethod, expectedType);
    }
    myPresentableTypeArgs = myNeedExplicitTypeParameters ? getTypeParamsText(true, myMethod, myInferenceSubstitutor) : null;
    if (myPresentableTypeArgs != null && myPresentableTypeArgs.length() > 10) {
      myPresentableTypeArgs = myPresentableTypeArgs.substring(0, 10) + "...>";
    }
  }

  public JavaMethodCallElement setQualifierSubstitutor(@NotNull PsiSubstitutor qualifierSubstitutor) {
    myQualifierSubstitutor = qualifierSubstitutor;
    return this;
  }

  @NotNull
  public PsiSubstitutor getSubstitutor() {
    return myQualifierSubstitutor;
  }

  @NotNull
  public PsiSubstitutor getInferenceSubstitutor() {
    return myInferenceSubstitutor;
  }

  @Override
  public void setShouldBeImported(boolean shouldImportStatic) {
    myHelper.setShouldBeImported(shouldImportStatic);
  }

  @Override
  public boolean canBeImported() {
    return myHelper != null;
  }

  @Override
  public boolean willBeImported() {
    return canBeImported() && myHelper.willBeImported();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof JavaMethodCallElement)) return false;
    if (!super.equals(o)) return false;
    if (!Objects.equals(myPresentableTypeArgs, ((JavaMethodCallElement)o).myPresentableTypeArgs)) return false;

    return myQualifierSubstitutor.equals(((JavaMethodCallElement)o).myQualifierSubstitutor);
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + (myPresentableTypeArgs == null ? 0 : myPresentableTypeArgs.hashCode());
    result = 31 * result + myQualifierSubstitutor.hashCode();
    return result;
  }

  @Override
  public void handleInsert(@NotNull InsertionContext context) {
    final Document document = context.getDocument();
    final PsiFile file = context.getFile();
    final PsiMethod method = getObject();

    final LookupElement[] allItems = context.getElements();
    ThreeState hasParams = method.getParameterList().isEmpty() ? ThreeState.NO : MethodParenthesesHandler.overloadsHaveParameters(allItems, method);
    if (method.isConstructor()) {
      PsiClass aClass = method.getContainingClass();
      if (aClass != null && aClass.getTypeParameters().length > 0) {
        document.insertString(context.getTailOffset(), "<>");
      }
    }
    JavaCompletionUtil.insertParentheses(context, this, false, hasParams, false);

    final int startOffset = context.getStartOffset();
    final OffsetKey refStart = context.trackOffset(startOffset, true);
    if (myNeedExplicitTypeParameters) {
      qualifyMethodCall(file, startOffset, document);
      insertExplicitTypeParameters(context, refStart);
    }
    else if (myHelper != null) {
      context.commitDocument();
      importOrQualify(document, file, method, startOffset);
    }

    PsiCallExpression methodCall = findCallAtOffset(context, context.getOffset(refStart));
    // make sure this is the method call we've just added, not the enclosing one
    if (methodCall != null) {
      PsiElement completedElement = methodCall instanceof PsiMethodCallExpression ?
                                    ((PsiMethodCallExpression)methodCall).getMethodExpression().getReferenceNameElement() : null;
      TextRange completedElementRange = completedElement == null ? null : completedElement.getTextRange();
      if (completedElementRange == null || completedElementRange.getStartOffset() != context.getStartOffset()) {
        methodCall = null;
      }
    }
    if (methodCall != null) {
      CompletionMemory.registerChosenMethod(method, methodCall);
      handleNegation(context, document, methodCall);
    }

    startArgumentLiveTemplate(context, method);
    showParameterHints(this, context, method, methodCall);
  }

  static PsiCallExpression findCallAtOffset(InsertionContext context, int offset) {
    context.commitDocument();
    return PsiTreeUtil.findElementOfClassAtOffset(context.getFile(), offset, PsiCallExpression.class, false);
  }

  private void handleNegation(InsertionContext context, Document document, PsiCallExpression methodCall) {
    if (context.getCompletionChar() == '!' && myNegatable) {
      context.setAddCompletionChar(false);
      FeatureUsageTracker.getInstance().triggerFeatureUsed(CodeCompletionFeatures.EXCLAMATION_FINISH);
      document.insertString(methodCall.getTextRange().getStartOffset(), "!");
    }
  }

  private void importOrQualify(Document document, PsiFile file, PsiMethod method, int startOffset) {
    if (willBeImported()) {
      if (method.isConstructor()) {
        final PsiNewExpression newExpression = PsiTreeUtil.findElementOfClassAtOffset(file, startOffset, PsiNewExpression.class, false);
        if (newExpression != null) {
          PsiJavaCodeReferenceElement ref = newExpression.getClassReference();
          if (ref != null && myContainingClass != null && !ref.isReferenceTo(myContainingClass)) {
            ref.bindToElement(myContainingClass);
            return;
          }
        }
      } else {
        final PsiReferenceExpression ref = PsiTreeUtil.findElementOfClassAtOffset(file, startOffset, PsiReferenceExpression.class, false);
        if (ref != null && myContainingClass != null && !ref.isReferenceTo(method)) {
          ref.bindToElementViaStaticImport(myContainingClass);
        }
        return;
      }
    }

    qualifyMethodCall(file, startOffset, document);
  }

  public static final Key<PsiMethod> ARGUMENT_TEMPLATE_ACTIVE = Key.create("ARGUMENT_TEMPLATE_ACTIVE");
  @NotNull
  private static Template createArgTemplate(PsiMethod method,
                                            int caretOffset,
                                            PsiExpressionList argList,
                                            TextRange argRange) {
    Template template = TemplateManager.getInstance(method.getProject()).createTemplate("", "");
    PsiParameter[] parameters = method.getParameterList().getParameters();
    for (int i = 0; i < parameters.length; i++) {
      if (i > 0) {
        template.addTextSegment(", ");
      }
      String name = parameters[i].getName();
      Expression expression = Registry.is("java.completion.argument.live.template.completion") ? new AutoPopupCompletion() : new ConstantNode(name);
      template.addVariable(name, expression, new ConstantNode(name), true);
    }
    boolean finishInsideParens = method.isVarArgs();
    if (finishInsideParens) {
      template.addEndVariable();
    }
    template.addTextSegment(argList.getText().substring(caretOffset - argRange.getStartOffset(), argList.getTextLength()));
    if (!finishInsideParens) {
      template.addEndVariable();
    }
    return template;
  }

  public static boolean startArgumentLiveTemplate(InsertionContext context, PsiMethod method) {
    if (method.getParameterList().isEmpty() ||
        context.getCompletionChar() == Lookup.COMPLETE_STATEMENT_SELECT_CHAR ||
        !ParameterInfoControllerBase.areParameterTemplatesEnabledOnCompletion()) {
      return false;
    }

    Editor editor = context.getEditor();
    context.commitDocument();
    PsiDocumentManager.getInstance(context.getProject()).doPostponedOperationsAndUnblockDocument(editor.getDocument());

    PsiCall call = PsiTreeUtil.findElementOfClassAtOffset(context.getFile(), context.getStartOffset(), PsiCall.class, false);
    PsiExpressionList argList = call == null ? null : call.getArgumentList();
    if (argList == null || !argList.isEmpty()) {
      return false;
    }

    TextRange argRange = argList.getTextRange();
    int caretOffset = editor.getCaretModel().getOffset();
    if (!argRange.contains(caretOffset)) {
      return false;
    }

    Template template = createArgTemplate(method, caretOffset, argList, argRange);

    context.getDocument().deleteString(caretOffset, argRange.getEndOffset());
    TemplateState templateState = TemplateManager.getInstance(method.getProject()).runTemplate(editor, template);

    setupNonFilledArgumentRemoving(editor, templateState);

    editor.putUserData(ARGUMENT_TEMPLATE_ACTIVE, method);
    Disposer.register(templateState, () -> {
      if (editor.getUserData(ARGUMENT_TEMPLATE_ACTIVE) == method) {
        editor.putUserData(ARGUMENT_TEMPLATE_ACTIVE, null);
      }
    });
    return true;
  }

  public static void showParameterHints(LookupElement element, InsertionContext context, PsiMethod method, PsiCallExpression methodCall) {
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

    int limit = getCompletionHintsLimit();

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

    setCompletionMode(methodCall, true);
    context.setLaterRunnable(() -> {
      Object[] itemsToShow = infoContext.getItemsToShow();
      PsiExpressionList methodCallArgumentList = methodCall.getArgumentList();
      ParameterInfoControllerBase controller =
        ParameterInfoControllerBase.createParameterInfoController(project, editor, braceOffset, itemsToShow, null,
                                                                  methodCallArgumentList, handler, false, false);
      Disposable hintsDisposal = () -> setCompletionMode(methodCall, false);
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

  public static int getCompletionHintsLimit() {
    return Math.max(1, Registry.intValue("editor.completion.hints.per.call.limit"));
  }

  public static void setCompletionModeIfNotSet(@NotNull PsiCall expression, @NotNull Disposable disposable) {
    if (!isCompletionMode(expression)) {
      setCompletionMode(expression, true);
      Disposer.register(disposable, () -> setCompletionMode(expression, false));
    }
  }

  public static void setCompletionMode(@NotNull PsiCall expression, boolean value) {
    expression.putUserData(COMPLETION_HINTS, value ? Boolean.TRUE : null);
  }

  public static boolean isCompletionMode(@NotNull PsiCall expression) {
    return expression.getUserData(COMPLETION_HINTS) != null;
  }

  private static void setupNonFilledArgumentRemoving(final Editor editor, final TemplateState templateState) {
    AtomicInteger maxEditedVariable = new AtomicInteger(-1);
    editor.getDocument().addDocumentListener(new DocumentListener() {
      @Override
      public void documentChanged(@NotNull DocumentEvent e) {
        maxEditedVariable.set(Math.max(maxEditedVariable.get(), templateState.getCurrentVariableNumber()));
      }
    }, templateState);

    templateState.addTemplateStateListener(new TemplateEditingAdapter() {
      @Override
      public void currentVariableChanged(@NotNull TemplateState templateState, Template template, int oldIndex, int newIndex) {
        maxEditedVariable.set(Math.max(maxEditedVariable.get(), oldIndex));
      }

      @Override
      public void beforeTemplateFinished(@NotNull TemplateState state, Template template, boolean brokenOff) {
        if (brokenOff) {
          removeUntouchedArguments((TemplateImpl)template);
        }
      }

      private void removeUntouchedArguments(TemplateImpl template) {
        int firstUnchangedVar = maxEditedVariable.get() + 1;
        if (firstUnchangedVar >= template.getVariableCount()) return;

        TextRange startRange = templateState.getVariableRange(template.getVariableNameAt(firstUnchangedVar));
        TextRange endRange = templateState.getVariableRange(template.getVariableNameAt(template.getVariableCount() - 1));
        if (startRange == null || endRange == null) return;

        WriteCommandAction.runWriteCommandAction(editor.getProject(), () ->
          editor.getDocument().deleteString(startRange.getStartOffset(), endRange.getEndOffset()));
      }
    });
  }

  private static boolean mayNeedTypeParameters(@NotNull final PsiElement leaf) {
    if (PsiTreeUtil.getParentOfType(leaf, PsiExpressionList.class, true, PsiCodeBlock.class, PsiModifierListOwner.class) == null) {
      if (PsiTreeUtil.getParentOfType(leaf, PsiConditionalExpression.class, true, PsiCodeBlock.class, PsiModifierListOwner.class) == null) {
        return false;
      }
    }

    if (PsiUtil.getLanguageLevel(leaf).isAtLeast(LanguageLevel.JDK_1_8)) return false;

    final PsiElement parent = leaf.getParent();
    if (parent instanceof PsiReferenceExpression && ((PsiReferenceExpression)parent).getTypeParameters().length > 0) {
      return false;
    }
    return true;
  }

  private void insertExplicitTypeParameters(InsertionContext context, OffsetKey refStart) {
    context.commitDocument();

    final String typeParams = getTypeParamsText(false, getObject(), getInferenceSubstitutor());
    if (typeParams != null) {
      context.getDocument().insertString(context.getOffset(refStart), typeParams);
      JavaCompletionUtil.shortenReference(context.getFile(), context.getOffset(refStart));
    }
  }

  private void qualifyMethodCall(PsiFile file, final int startOffset, final Document document) {
    final PsiReference reference = file.findReferenceAt(startOffset);
    if (reference instanceof PsiReferenceExpression && ((PsiReferenceExpression)reference).isQualified()) {
      return;
    }

    final PsiMethod method = getObject();
    if (method.isConstructor()) return;
    if (!method.hasModifierProperty(PsiModifier.STATIC)) {
      document.insertString(startOffset, "this.");
      return;
    }

    if (myContainingClass == null) return;

    document.insertString(startOffset, ".");
    JavaCompletionUtil.insertClassReference(myContainingClass, file, startOffset);
  }

  @Nullable
  public static String getTypeParamsText(boolean presentable, PsiTypeParameterListOwner owner, PsiSubstitutor substitutor) {
    PsiTypeParameter[] parameters = owner.getTypeParameters();
    if (parameters.length == 0) return null;

    List<PsiType> substituted = ContainerUtil.map(parameters, parameter -> {
      PsiType type = substitutor.substitute(parameter);
      if (type instanceof PsiWildcardType) type = ((PsiWildcardType)type).getExtendsBound();
      return PsiUtil.resolveClassInClassTypeOnly(type) == parameter ? null : type;
    });
    if (ContainerUtil.exists(substituted, t -> t == null || t instanceof PsiCapturedWildcardType)) return null;
    if (substituted.equals(ContainerUtil.map(parameters, TypeConversionUtil::typeParameterErasure))) return null;

    String result = "<" + StringUtil.join(substituted, presentable ? PsiType::getPresentableText : PsiType::getCanonicalText, ", ") + ">";
    return result.contains("?") ? null : result;
  }

  @Override
  public boolean isValid() {
    return super.isValid() && myInferenceSubstitutor.isValid() && getSubstitutor().isValid();
  }

  @Override
  public void renderElement(@NotNull LookupElementPresentation presentation) {
    presentation.setIcon(DefaultLookupItemRenderer.getRawIcon(this));

    presentation.setStrikeout(JavaElementLookupRenderer.isToStrikeout(this));

    MemberLookupHelper helper = myHelper != null ? myHelper : new MemberLookupHelper(myMethod, myContainingClass, false, false);
    helper.renderElement(presentation, myHelper != null, myHelper != null && !myHelper.willBeImported(), getSubstitutor());
    if (!myForcedQualifier.isEmpty()) {
      presentation.setItemText(myForcedQualifier + presentation.getItemText());
    }

    if (myPresentableTypeArgs != null) {
      String itemText = presentation.getItemText();
      assert itemText != null;
      int i = itemText.indexOf('.');
      if (i > 0) {
        presentation.setItemText(itemText.substring(0, i + 1) + myPresentableTypeArgs + itemText.substring(i + 1));
      }
    }
  }

  @Override
  public boolean isWorthShowingInAutoPopup() {
    // We always have method parameters
    return true;
  }

  private static class AutoPopupCompletion extends Expression {
    @Nullable
    @Override
    public Result calculateResult(ExpressionContext context) {
      return new InvokeActionResult(() -> AutoPopupController.getInstance(context.getProject()).scheduleAutoPopup(context.getEditor()));
    }

    @Nullable
    @Override
    public Result calculateQuickResult(ExpressionContext context) {
      return null;
    }

    @Override
    public LookupElement @Nullable [] calculateLookupItems(ExpressionContext context) {
      return null;
    }
  }
}
