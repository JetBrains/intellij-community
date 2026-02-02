// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.AutoPopupController;
import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.lookup.DefaultLookupItemRenderer;
import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementPresentation;
import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.codeInsight.lookup.TypedLookupItem;
import com.intellij.codeInsight.lookup.impl.JavaElementLookupRenderer;
import com.intellij.codeInsight.template.Expression;
import com.intellij.codeInsight.template.ExpressionContext;
import com.intellij.codeInsight.template.InvokeActionResult;
import com.intellij.codeInsight.template.Result;
import com.intellij.codeInsight.template.Template;
import com.intellij.codeInsight.template.TemplateEditingAdapter;
import com.intellij.codeInsight.template.TemplateManager;
import com.intellij.codeInsight.template.impl.ConstantNode;
import com.intellij.codeInsight.template.impl.TemplateImpl;
import com.intellij.codeInsight.template.impl.TemplateState;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.util.ClassConditionKey;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.GenericsUtil;
import com.intellij.psi.PsiCall;
import com.intellij.psi.PsiCapturedWildcardType;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiConditionalExpression;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpressionList;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeParameter;
import com.intellij.psi.PsiTypeParameterListOwner;
import com.intellij.psi.PsiTypes;
import com.intellij.psi.PsiWildcardType;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

public class JavaMethodCallElement extends LookupItem<PsiMethod> implements TypedLookupItem, StaticallyImportable, LookupElementWithEffectiveInsertHandler {
  public static final ClassConditionKey<JavaMethodCallElement> CLASS_CONDITION_KEY = ClassConditionKey.create(JavaMethodCallElement.class);
  public static final Key<Boolean> COMPLETION_HINTS = Key.create("completion.hints");
  private final @Nullable PsiClass myContainingClass;
  private final @NotNull PsiMethod myMethod;
  private final MemberLookupHelper myHelper;
  private final boolean myNegatable;
  private PsiSubstitutor myQualifierSubstitutor = PsiSubstitutor.EMPTY;
  private PsiSubstitutor myInferenceSubstitutor = PsiSubstitutor.EMPTY;
  private boolean myNeedExplicitTypeParameters;
  private String myForcedQualifier = "";
  private @Nullable String myPresentableTypeArgs;

  public JavaMethodCallElement(@NotNull PsiMethod method) {
    this(method, null);
  }

  private JavaMethodCallElement(@NotNull PsiMethod method, @Nullable MemberLookupHelper helper) {
    super(method, method.isConstructor() ? "new " + method.getName() : method.getName());
    myMethod = method;
    myContainingClass = method.getContainingClass();
    myHelper = helper;
    PsiType type = method.getReturnType();
    myNegatable = type != null && PsiTypes.booleanType().isAssignableFrom(type);
  }

  public JavaMethodCallElement(@NotNull PsiMethod method, boolean shouldImportStatic, boolean mergedOverloads) {
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

  protected boolean needExplicitTypeParameters() {
    return myNeedExplicitTypeParameters;
  }

  MemberLookupHelper getHelper() {
    return myHelper;
  }

  @Nullable PsiClass getContainingClass() {
    return myContainingClass;
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

  public @NotNull PsiSubstitutor getSubstitutor() {
    return myQualifierSubstitutor;
  }

  public @NotNull PsiSubstitutor getInferenceSubstitutor() {
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
    var handler = createInsertHandler();
    handler.handleInsert(context, this);
  }

  private @NotNull JavaMethodCallInsertHandler createInsertHandler() {
    return new JavaMethodCallInsertHandler(myNeedExplicitTypeParameters, null, null, true, true, this);
  }

  @Override
  public @Nullable InsertHandler<?> getEffectiveInsertHandler() {
    return createInsertHandler();
  }

  public static final Key<PsiMethod> ARGUMENT_TEMPLATE_ACTIVE = Key.create("ARGUMENT_TEMPLATE_ACTIVE");

  private static @NotNull Template createArgTemplate(PsiMethod method,
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

  public static boolean areParameterTemplatesEnabledOnCompletion() {
    return Registry.is("java.completion.argument.live.template") &&
           !CodeInsightSettings.getInstance().SHOW_PARAMETER_NAME_HINTS_ON_COMPLETION;
  }

  public static boolean startArgumentLiveTemplate(InsertionContext context, PsiMethod method) {
    if (method.getParameterList().isEmpty() ||
        context.getCompletionChar() == Lookup.COMPLETE_STATEMENT_SELECT_CHAR ||
        !areParameterTemplatesEnabledOnCompletion()) {
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

  @ApiStatus.Internal
  public static boolean mayNeedTypeParameters(final @NotNull PsiElement leaf) {
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

  public static @Nullable String getTypeParamsText(boolean presentable, PsiTypeParameterListOwner owner, PsiSubstitutor substitutor) {
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
    boolean isAutoImportName = myContainingClass != null &&
                               JavaCodeStyleManager.getInstance(myMethod.getProject())
                                 .isStaticAutoImportName(myContainingClass.getQualifiedName() + "." + myMethod.getName());
    MemberLookupHelper helper = myHelper != null ? myHelper : new MemberLookupHelper(myMethod, myContainingClass, false, false);
    boolean showPackage = myHelper != null && (!myHelper.willBeImported() || isAutoImportName);
    helper.renderElement(presentation, myHelper != null, showPackage, getSubstitutor());
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
    @Override
    public @Nullable Result calculateResult(ExpressionContext context) {
      return new InvokeActionResult(() -> AutoPopupController.getInstance(context.getProject()).scheduleAutoPopup(context.getEditor()));
    }

    @Override
    public @Nullable Result calculateQuickResult(ExpressionContext context) {
      return null;
    }

    @Override
    public LookupElement @Nullable [] calculateLookupItems(ExpressionContext context) {
      return null;
    }
  }
}
