// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.lookup;

import com.intellij.codeInsight.AutoPopupController;
import com.intellij.codeInsight.TailType;
import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.daemon.impl.JavaColorProvider;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightControlFlowUtil;
import com.intellij.codeInsight.daemon.impl.quickfix.BringVariableIntoScopeFix;
import com.intellij.codeInsight.lookup.impl.JavaElementLookupRenderer;
import com.intellij.codeInspection.dataFlow.jvm.descriptors.PlainDescriptor;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.util.RecursionManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.psi.impl.source.PsiFieldImpl;
import com.intellij.psi.impl.source.resolve.JavaResolveUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ui.ColorIcon;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.HashMap;
import java.util.Objects;

public class VariableLookupItem extends LookupItem<PsiVariable> implements TypedLookupItem, StaticallyImportable {
  private static final String EQ = " = ";
  @Nullable private final MemberLookupHelper myHelper;
  private final Color myColor;
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
    myColor = getInitializerColor(var);
    myTailText = tailText == null ? getInitializerText(var) : tailText;
    myNegatable = PsiTypes.booleanType().isAssignableFrom(var.getType());
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

  @Nullable
  private String getInitializerText(PsiVariable var) {
    if (myColor != null || !var.hasModifierProperty(PsiModifier.FINAL) || !var.hasModifierProperty(PsiModifier.STATIC)) return null;
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

  @Nullable
  private static Color getInitializerColor(@NotNull PsiVariable var) {
    if (!JavaColorProvider.isColorType(var.getType())) return null;

    PsiExpression expression = getInitializer(var);
    if (expression instanceof PsiReferenceExpression) {
      final PsiElement target = ((PsiReferenceExpression)expression).resolve();
      if (target instanceof PsiVariable) {
        return RecursionManager.doPreventingRecursion(expression, true, () -> getInitializerColor((PsiVariable)target));
      }
    }
    return JavaColorProvider.getJavaColorFromExpression(expression);
  }

  @Override
  @NotNull
  public PsiType getType() {
    return getSubstitutor().substitute(getObject().getType());
  }

  @NotNull
  public PsiSubstitutor getSubstitutor() {
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
    if (myColor != null) {
      presentation.setTypeText("", JBUIScale.scaleIcon(new ColorIcon(12, myColor)));
    } else {
      presentation.setTypeText(getType().getPresentableText());
    }
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

    if (variable instanceof PsiField) {
      if (willBeImported()) {
        RangeMarker toDelete = JavaCompletionUtil.insertTemporary(context.getTailOffset(), document, " ");
        context.commitDocument();
        PsiReferenceExpression ref = findReference(context, context.getStartOffset());
        if (ref != null) {
          if (ref.isQualified()) {
            return; // shouldn't happen, but sometimes we see exceptions because of this
          }
          ref.bindToElementViaStaticImport(((PsiField)variable).getContainingClass());
          PostprocessReformattingAspect.getInstance(ref.getProject()).doPostponedFormatting();
        }
        if (toDelete != null && toDelete.isValid()) {
          document.deleteString(toDelete.getStartOffset(), toDelete.getEndOffset());
        }
        context.commitDocument();
      }
      else if (shouldQualify((PsiField)variable, context)) {
        qualifyFieldReference(context, (PsiField)variable);
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
      if (fix != null && fix.isAvailable(context.getProject(), context.getEditor(), context.getFile())) {
        fix.invoke(context.getProject(), context.getEditor(), context.getFile());
      }
    }

    final char completionChar = context.getCompletionChar();
    if (completionChar == '=') {
      context.setAddCompletionChar(false);
      EqTailType.INSTANCE.processTail(context.getEditor(), context.getTailOffset());
    }
    else if (completionChar == ',' && getAttribute(LookupItem.TAIL_TYPE_ATTR) != TailType.UNKNOWN) {
      context.setAddCompletionChar(false);
      CommaTailType.INSTANCE.processTail(context.getEditor(), context.getTailOffset());
      AutoPopupController.getInstance(context.getProject()).autoPopupParameterInfo(context.getEditor(), null);
    }
    else if (completionChar == ':' && getAttribute(LookupItem.TAIL_TYPE_ATTR) != TailType.UNKNOWN && isTernaryCondition(ref)) {
      context.setAddCompletionChar(false);
      TailType.COND_EXPR_COLON.processTail(context.getEditor(), context.getTailOffset());
    }
    else if (completionChar == '.') {
      AutoPopupController.getInstance(context.getProject()).autoPopupMemberLookup(context.getEditor(), null);
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
    if (place == null || PsiUtil.isLanguageLevel8OrHigher(place) || JspPsiUtil.isInJspFile(place)) {
      return;
    }

    if (HighlightControlFlowUtil.getElementVariableReferencedFrom(variable, place) != null &&
        !HighlightControlFlowUtil.isReassigned(variable, new HashMap<>())) {
      PsiUtil.setModifierProperty(variable, PsiModifier.FINAL, true);
    }
  }

  private boolean shouldQualify(PsiField field, InsertionContext context) {
    if (myHelper != null && !myHelper.willBeImported()) {
      return true;
    }

    return shouldQualify(field, context.getFile().findReferenceAt(context.getTailOffset() - 1));
  }

  private static boolean shouldQualify(@NotNull PsiField field, @Nullable PsiReference context) {
    if ((context instanceof PsiReferenceExpression && !((PsiReferenceExpression)context).isQualified()) || 
        (context instanceof PsiJavaCodeReferenceElement && !((PsiJavaCodeReferenceElement)context).isQualified())) {
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
