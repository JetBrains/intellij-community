// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.lookup;

import com.intellij.codeInsight.AutoPopupController;
import com.intellij.codeInsight.TailTypes;
import com.intellij.codeInsight.completion.CodeCompletionFeatures;
import com.intellij.codeInsight.completion.CompletionUtil;
import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.codeInsight.completion.JavaCompletionUtil;
import com.intellij.codeInsight.completion.MemberLookupHelper;
import com.intellij.codeInsight.completion.PrioritizedLookupElement;
import com.intellij.codeInsight.completion.StaticallyImportable;
import com.intellij.codeInsight.daemon.impl.quickfix.BringVariableIntoScopeFix;
import com.intellij.codeInsight.lookup.impl.JavaElementLookupRenderer;
import com.intellij.codeInspection.dataFlow.jvm.descriptors.PlainDescriptor;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModCommandExecutor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.java.JavaFeature;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.JspPsiUtil;
import com.intellij.psi.PsiCaseLabelElementList;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiConditionalExpression;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiEnumConstant;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiExpressionList;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.PsiSwitchBlock;
import com.intellij.psi.PsiSwitchLabelStatementBase;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiVariable;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.controlFlow.ControlFlowUtil;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.psi.impl.source.PsiFieldImpl;
import com.intellij.psi.impl.source.resolve.JavaResolveUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Objects;

public class VariableLookupItem extends LookupItem<PsiVariable> implements TypedLookupItem, StaticallyImportable {
  @ApiStatus.Internal
  public static final String EQ = " = ";
  private final @Nullable MemberLookupHelper myHelper;
  private final String myTailText;
  private final boolean myNegatable;
  private PsiSubstitutor mySubstitutor = PsiSubstitutor.EMPTY;
  private String myForcedQualifier;
  private PsiClass myQualifierClass;

  public VariableLookupItem(PsiVariable var) {
    this(var, null, null);
  }

  public VariableLookupItem(PsiField field, boolean shouldImport) {
    this(field, new MemberLookupHelper(field, field.getContainingClass(), shouldImport, false), null);
  }

  /**
   * @param var      variable to lookup
   * @param tailText specific tail text to insert (initializer text is used if not specified)
   */
  public VariableLookupItem(@NotNull PsiVariable var, @NotNull @Nls String tailText) {
    this(var, null, tailText);
  }

  private VariableLookupItem(@NotNull PsiVariable var, @Nullable MemberLookupHelper helper, @Nullable @Nls String tailText) {
    super(var, Objects.requireNonNull(var.getName()));
    myHelper = helper;
    myTailText = tailText == null ? getInitializerText(var) : tailText;
    myNegatable = TypeConversionUtil.isBooleanType(var.getType());
  }

  @ApiStatus.Internal
  public LookupElement qualifyIfNeeded(@Nullable PsiReference position, @Nullable PsiClass origClass) {
    PsiVariable var = getObject();
    if (var instanceof PsiField && !willBeImported() && shouldQualify((PsiField)var, position)) {
      boolean isInstanceField = !var.hasModifierProperty(PsiModifier.STATIC);
      PsiClass aClass = ((PsiField)var).getContainingClass();
      if (aClass != null && origClass != null &&
          !JavaResolveUtil.isAccessible(aClass, aClass.getContainingClass(), aClass.getModifierList(), position.getElement(), null, null) &&
          JavaResolveUtil.isAccessible(origClass, origClass.getContainingClass(), origClass.getModifierList(), position.getElement(), null,
                                       null) &&
          var.isEquivalentTo(origClass.findFieldByName(var.getName(), true))) {
        aClass = origClass;
      }
      String className = aClass == null ? null : aClass.getName();
      if (className != null) {
        String infix = isInstanceField ? ".this." : ".";
        myQualifierClass = aClass;
        myForcedQualifier = className + infix;
        for (String s : JavaCompletionUtil.getAllLookupStrings(aClass)) {
          setLookupString(s + infix + var.getName());
        }
        if (isInstanceField) {
          return PrioritizedLookupElement.withExplicitProximity(this, -1);
        }
      }
    }
    return this;
  }

  /**
   * @param var variable to get its initializer text
   * @return the initializer text to display in completion popup; null if not applicable
   */
  @ApiStatus.Internal
  public static @Nullable String getInitializerText(PsiVariable var) {
    if (!var.hasModifierProperty(PsiModifier.FINAL) || !var.hasModifierProperty(PsiModifier.STATIC)) return null;
    if (PlainDescriptor.hasInitializationHacks(var)) return null;

    PsiElement initializer = var instanceof PsiEnumConstant ? ((PsiEnumConstant)var).getArgumentList() : getInitializer(var);
    String initText = initializer == null ? null : initializer.getText();
    if (StringUtil.isEmpty(initText)) return null;

    String prefix = var instanceof PsiEnumConstant ? "" : EQ;
    String suffix = var instanceof PsiEnumConstant && ((PsiEnumConstant)var).getInitializingClass() != null ? " {...}" : "";
    return StringUtil.trimLog(prefix + initText + suffix, 30);
  }

  private static PsiExpression getInitializer(@NotNull PsiVariable var) {
    PsiElement navigationElement = var.getNavigationElement();
    if (navigationElement instanceof PsiVariable) {
      var = (PsiVariable)navigationElement;
    }
    return PsiFieldImpl.getDetachedInitializer(var);
  }

  @Override
  public @NotNull PsiType getType() {
    return getSubstitutor().substitute(getObject().getType());
  }

  public @NotNull PsiSubstitutor getSubstitutor() {
    return mySubstitutor;
  }

  public VariableLookupItem setSubstitutor(@NotNull PsiSubstitutor substitutor) {
    mySubstitutor = substitutor;
    return this;
  }

  @Override
  public void setShouldBeImported(boolean shouldImportStatic) {
    assert myHelper != null;
    myHelper.setShouldBeImported(shouldImportStatic);
  }

  @Override
  public boolean canBeImported() {
    return myHelper != null;
  }

  @Override
  public boolean willBeImported() {
    return myHelper != null && myHelper.willBeImported();
  }

  @Override
  public void renderElement(@NotNull LookupElementPresentation presentation) {
    boolean qualify = myHelper != null && !myHelper.willBeImported() || myForcedQualifier != null;

    PsiVariable variable = getObject();
    String name = variable.getName();
    if (qualify && variable instanceof PsiField && ((PsiField)variable).getContainingClass() != null) {
      name = (myForcedQualifier != null ? myForcedQualifier : ((PsiField)variable).getContainingClass().getName() + ".") + name;
    }
    presentation.setItemText(name);

    presentation.setIcon(DefaultLookupItemRenderer.getRawIcon(this));
    presentation.setStrikeout(JavaElementLookupRenderer.isToStrikeout(this));

    if (myTailText != null) {
      if (myTailText.startsWith(EQ)) {
        presentation.appendTailTextItalic(" (" + myTailText + ")", true);
      } else {
        presentation.setTailText(myTailText, true);
      }
    }
    if (myHelper != null) {
      myHelper.renderElement(presentation, qualify, true, getSubstitutor());
    }
    presentation.setTypeText(getType().getPresentableText());
  }

  public boolean isNegatable() {
    return myNegatable;
  }

  @Override
  public void handleInsert(@NotNull InsertionContext context) {
    PsiVariable variable = getObject();

    Document document = context.getDocument();
    document.replaceString(context.getStartOffset(), context.getTailOffset(), variable.getName());
    context.commitDocument();

    if (variable instanceof PsiField field) {
      if (willBeImported()) {
        RangeMarker toDelete = JavaCompletionUtil.insertTemporary(context.getTailOffset(), document, " ");
        context.commitDocument();
        PsiReferenceExpression ref = findReference(context, context.getStartOffset());
        if (ref != null) {
          if (ref.isQualified()) {
            return; // shouldn't happen, but sometimes we see exceptions because of this
          }
          ref.bindToElementViaStaticImport(field.getContainingClass());
          PostprocessReformattingAspect.getInstance(ref.getProject()).doPostponedFormatting();
        }
        if (toDelete != null && toDelete.isValid()) {
          document.deleteString(toDelete.getStartOffset(), toDelete.getEndOffset());
        }
        context.commitDocument();
      }
      else if (shouldQualify(field, context)) {
        qualifyFieldReference(context, field);
      }
    }

    PsiReferenceExpression ref = findReference(context, context.getTailOffset() - 1);
    if (ref != null) {
      JavaCodeStyleManager.getInstance(context.getProject()).shortenClassReferences(ref);
    }

    ref = findReference(context, context.getTailOffset() - 1);
    PsiElement target = ref == null ? null : ref.resolve();
    if (target instanceof PsiLocalVariable || target instanceof PsiParameter) {
      makeFinalIfNeeded(context, (PsiVariable)target);
    }
    if (target == null && ref != null &&
        JavaPsiFacade.getInstance(context.getProject()).getResolveHelper().resolveReferencedVariable(variable.getName(), ref) == null) {
      BringVariableIntoScopeFix fix = BringVariableIntoScopeFix.fromReference(ref);
      if (fix != null) {
        ActionContext actionContext = ActionContext.from(context.getEditor(), context.getFile());
        if (fix.getPresentation(actionContext) != null) {
          ModCommandExecutor.getInstance().executeInteractively(actionContext, fix.perform(actionContext), context.getEditor());
        }
      }
    }

    final char completionChar = context.getCompletionChar();
    if (completionChar == '=') {
      context.setAddCompletionChar(false);
      EqTailType.INSTANCE.processTail(context.getEditor(), context.getTailOffset());
    }
    else if (completionChar == ',' && getAttribute(LookupItem.TAIL_TYPE_ATTR) != TailTypes.unknownType()) {
      context.setAddCompletionChar(false);
      CommaTailType.INSTANCE.processTail(context.getEditor(), context.getTailOffset());
      AutoPopupController.getInstance(context.getProject()).autoPopupParameterInfo(context.getEditor(), null);
    }
    else if (completionChar == ':' && getAttribute(LookupItem.TAIL_TYPE_ATTR) != TailTypes.unknownType() && isTernaryCondition(ref)) {
      context.setAddCompletionChar(false);
      TailTypes.conditionalExpressionColonType().processTail(context.getEditor(), context.getTailOffset());
    }
    else if (completionChar == '.') {
      AutoPopupController.getInstance(context.getProject()).scheduleAutoPopup(context.getEditor());
    }
    else if (completionChar == '!' && myNegatable) {
      context.setAddCompletionChar(false);
      if (ref != null) {
        FeatureUsageTracker.getInstance().triggerFeatureUsed(CodeCompletionFeatures.EXCLAMATION_FINISH);
        document.insertString(ref.getTextRange().getStartOffset(), "!");
      }
    }
    else if (completionChar == Lookup.REPLACE_SELECT_CHAR) {
      removeEmptyCallParentheses(context);
    }
  }

  private static void removeEmptyCallParentheses(@NotNull InsertionContext context) {
    PsiReferenceExpression ref = findReference(context, context.getTailOffset() - 1);
    if (ref != null && ref.getParent() instanceof PsiMethodCallExpression) {
      PsiExpressionList argList = ((PsiMethodCallExpression)ref.getParent()).getArgumentList();
      if (argList.getExpressionCount() == 0) {
        PsiDocumentManager.getInstance(argList.getProject()).doPostponedOperationsAndUnblockDocument(context.getDocument());
        context.getDocument().deleteString(argList.getTextRange().getStartOffset(), argList.getTextRange().getEndOffset());
      }
    }
  }

  private static PsiReferenceExpression findReference(@NotNull InsertionContext context, int offset) {
    return PsiTreeUtil.findElementOfClassAtOffset(context.getFile(), offset, PsiReferenceExpression.class, false);
  }

  private static boolean isTernaryCondition(PsiReferenceExpression ref) {
    PsiElement parent = ref == null ? null : ref.getParent();
    return parent instanceof PsiConditionalExpression && ref == ((PsiConditionalExpression)parent).getThenExpression();
  }

  public static void makeFinalIfNeeded(@NotNull InsertionContext context, @NotNull PsiVariable variable) {
    PsiElement place = context.getFile().findElementAt(context.getTailOffset() - 1);
    if (place == null || PsiUtil.isAvailable(JavaFeature.EFFECTIVELY_FINAL, place) || JspPsiUtil.isInJspFile(place)) {
      return;
    }

    if (ControlFlowUtil.getScopeEnforcingEffectiveFinality(variable, place) != null &&
        !ControlFlowUtil.isReassigned(variable, new HashMap<>())) {
      PsiUtil.setModifierProperty(variable, PsiModifier.FINAL, true);
    }
  }

  private boolean shouldQualify(PsiField field, InsertionContext context) {
    if (myHelper != null && !myHelper.willBeImported()) {
      return true;
    }

    return shouldQualify(field, context.getFile().findReferenceAt(context.getTailOffset() - 1));
  }

  /**
   * @param field field to check
   * @param context context where it's about to be used
   * @return whether the field access should be qualified in a given context
   */
  @ApiStatus.Internal
  public static boolean shouldQualify(@NotNull PsiField field, @Nullable PsiReference context) {
    if ((context instanceof PsiReferenceExpression ref && !ref.isQualified()) ||
        (context instanceof PsiJavaCodeReferenceElement codeRef && !codeRef.isQualified())) {
      PsiElement element = context.getElement();
      if (isEnumInSwitch(field, element)) return false;
      PsiVariable target = JavaPsiFacade.getInstance(element.getProject()).getResolveHelper()
        .resolveReferencedVariable(field.getName(), (PsiElement)context);
      return !field.getManager().areElementsEquivalent(target, field) &&
             !field.getManager().areElementsEquivalent(target, CompletionUtil.getOriginalOrSelf(field));
    }
    return false;
  }

  private static boolean isEnumInSwitch(@NotNull PsiField field, PsiElement element) {
    if (!(field instanceof PsiEnumConstant) || !(element.getParent() instanceof PsiCaseLabelElementList)) return false;
    PsiClass enumClass = field.getContainingClass();
    PsiSwitchLabelStatementBase label = ObjectUtils.tryCast(element.getParent().getParent(), PsiSwitchLabelStatementBase.class);
    if (label == null || enumClass == null) return false;
    PsiSwitchBlock block = label.getEnclosingSwitchBlock();
    if (block == null) return false;
    PsiExpression expression = block.getExpression();
    if (expression == null) return false;
    PsiType type = expression.getType();
    return type instanceof PsiClassType && enumClass.getManager().areElementsEquivalent(enumClass, ((PsiClassType)type).resolve());
  }

  private void qualifyFieldReference(InsertionContext context, PsiField field) {
    context.commitDocument();
    PsiFile file = context.getFile();
    final PsiReference reference = file.findReferenceAt(context.getStartOffset());
    if (reference instanceof PsiJavaCodeReferenceElement && ((PsiJavaCodeReferenceElement)reference).isQualified()) {
      return;
    }

    PsiClass containingClass = myQualifierClass != null && myQualifierClass.isValid() ? myQualifierClass : field.getContainingClass();
    if (containingClass != null && containingClass.getName() != null) {
      context.getDocument().insertString(context.getStartOffset(), field.hasModifierProperty(PsiModifier.STATIC) ? "." : ".this.");
      JavaCompletionUtil.insertClassReference(containingClass, file, context.getStartOffset());
      PsiDocumentManager.getInstance(context.getProject()).commitDocument(context.getDocument());
    }
  }

  @Override
  public boolean isWorthShowingInAutoPopup() {
    return myTailText != null;
  }
}
