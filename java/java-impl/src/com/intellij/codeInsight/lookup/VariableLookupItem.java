/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.codeInsight.lookup;

import com.intellij.codeInsight.AutoPopupController;
import com.intellij.codeInsight.TailType;
import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.daemon.impl.JavaColorProvider;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightControlFlowUtil;
import com.intellij.codeInsight.lookup.impl.JavaElementLookupRenderer;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.util.RecursionManager;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.psi.impl.source.PsiFieldImpl;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.containers.HashMap;
import com.intellij.util.ui.ColorIcon;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

/**
* @author peter
*/
public class VariableLookupItem extends LookupItem<PsiVariable> implements TypedLookupItem, StaticallyImportable {
  private static final String EQ = " = ";
  @Nullable private final MemberLookupHelper myHelper;
  private final Color myColor;
  private final String myTailText;
  private PsiSubstitutor mySubstitutor = PsiSubstitutor.EMPTY;

  public VariableLookupItem(PsiVariable var) {
    super(var, var.getName());
    myHelper = null;
    myColor = getInitializerColor(var);
    myTailText = getInitializerText(var);
  }

  public VariableLookupItem(PsiField field, boolean shouldImport) {
    super(field, field.getName());
    myHelper = new MemberLookupHelper(field, field.getContainingClass(), shouldImport, false);
    if (!shouldImport) {
      for (String s : JavaCompletionUtil.getAllLookupStrings(field)) {
        setLookupString(s); //todo set the string that will be inserted
      }
    }
    myColor = getInitializerColor(field);
    myTailText = getInitializerText(field);
  }

  @Nullable
  private String getInitializerText(PsiVariable var) {
    if (myColor != null || !var.hasModifierProperty(PsiModifier.FINAL) || !var.hasModifierProperty(PsiModifier.STATIC)) return null;

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
    return var instanceof PsiFieldImpl ? ((PsiFieldImpl)var).getDetachedInitializer() : var.getInitializer();
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
  public void renderElement(LookupElementPresentation presentation) {
    boolean qualify = myHelper != null && !myHelper.willBeImported();

    PsiVariable variable = getObject();
    String name = variable.getName();
    if (qualify && variable instanceof PsiField && ((PsiField)variable).getContainingClass() != null) {
      name = ((PsiField)variable).getContainingClass().getName() + "." + name;
    }
    presentation.setItemText(name);

    presentation.setIcon(DefaultLookupItemRenderer.getRawIcon(this, presentation.isReal()));
    presentation.setStrikeout(JavaElementLookupRenderer.isToStrikeout(this));

    if (myHelper != null) {
      myHelper.renderElement(presentation, qualify, true, getSubstitutor());
    }
    if (myColor != null) {
      presentation.setTypeText("", JBUI.scale(new ColorIcon(12, myColor)));
    } else {
      presentation.setTypeText(getType().getPresentableText());
    }
    if (myTailText != null && StringUtil.isEmpty(presentation.getTailText())) {
      if (myTailText.startsWith(EQ)) {
        presentation.appendTailTextItalic(" (" + myTailText + ")", true);
      } else {
        presentation.setTailText(myTailText, true);
      }
    }
  }

  @Override
  public void handleInsert(InsertionContext context) {
    PsiVariable variable = getObject();

    Document document = context.getDocument();
    document.replaceString(context.getStartOffset(), context.getTailOffset(), variable.getName());
    context.commitDocument();

    if (variable instanceof PsiField) {
      if (willBeImported()) {
        RangeMarker toDelete = JavaCompletionUtil.insertTemporary(context.getTailOffset(), document, " ");
        context.commitDocument();
        final PsiReferenceExpression
          ref = PsiTreeUtil.findElementOfClassAtOffset(context.getFile(), context.getStartOffset(), PsiReferenceExpression.class, false);
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

    PsiReferenceExpression ref = PsiTreeUtil.findElementOfClassAtOffset(context.getFile(), context.getTailOffset() - 1, PsiReferenceExpression.class, false);
    if (ref != null) {
      JavaCodeStyleManager.getInstance(context.getProject()).shortenClassReferences(ref);
    }

    ref = PsiTreeUtil.findElementOfClassAtOffset(context.getFile(), context.getTailOffset() - 1, PsiReferenceExpression.class, false);
    PsiElement target = ref == null ? null : ref.resolve();
    if (target instanceof PsiLocalVariable || target instanceof PsiParameter) {
      makeFinalIfNeeded(context, (PsiVariable)target);
    }

    final char completionChar = context.getCompletionChar();
    if (completionChar == '=') {
      context.setAddCompletionChar(false);
      TailType.EQ.processTail(context.getEditor(), context.getTailOffset());
    }
    else if (completionChar == ',' && getAttribute(LookupItem.TAIL_TYPE_ATTR) != TailType.UNKNOWN) {
      context.setAddCompletionChar(false);
      TailType.COMMA.processTail(context.getEditor(), context.getTailOffset());
      AutoPopupController.getInstance(context.getProject()).autoPopupParameterInfo(context.getEditor(), null);
    }
    else if (completionChar == ':' && getAttribute(LookupItem.TAIL_TYPE_ATTR) != TailType.UNKNOWN && isTernaryCondition(ref)) {
      context.setAddCompletionChar(false);
      TailType.COND_EXPR_COLON.processTail(context.getEditor(), context.getTailOffset());
    }
    else if (completionChar == '.') {
      AutoPopupController.getInstance(context.getProject()).autoPopupMemberLookup(context.getEditor(), null);
    }
    else if (completionChar == '!' && PsiType.BOOLEAN.isAssignableFrom(variable.getType())) {
      context.setAddCompletionChar(false);
      if (ref != null) {
        FeatureUsageTracker.getInstance().triggerFeatureUsed(CodeCompletionFeatures.EXCLAMATION_FINISH);
        document.insertString(ref.getTextRange().getStartOffset(), "!");
      }
    }
  }

  private static boolean isTernaryCondition(PsiReferenceExpression ref) {
    PsiElement parent = ref == null ? null : ref.getParent();
    return parent instanceof PsiConditionalExpression && ref == ((PsiConditionalExpression)parent).getThenExpression();
  }

  public static void makeFinalIfNeeded(@NotNull InsertionContext context, @NotNull PsiVariable variable) {
    PsiElement place = context.getFile().findElementAt(context.getTailOffset() - 1);
    if (!Registry.is("java.completion.make.outer.variables.final") ||
        place == null || PsiUtil.isLanguageLevel8OrHigher(place) || JspPsiUtil.isInJspFile(place)) {
      return;
    }

    if (HighlightControlFlowUtil.getInnerClassVariableReferencedFrom(variable, place) != null &&
        !HighlightControlFlowUtil.isReassigned(variable, new HashMap<>())) {
      PsiUtil.setModifierProperty(variable, PsiModifier.FINAL, true);
    }
  }

  private boolean shouldQualify(PsiField field, InsertionContext context) {
    if (myHelper != null && !myHelper.willBeImported()) {
      return true;
    }

    PsiReference reference = context.getFile().findReferenceAt(context.getStartOffset());
    if (reference instanceof PsiReferenceExpression && !((PsiReferenceExpression)reference).isQualified()) {
      final PsiVariable target = JavaPsiFacade.getInstance(context.getProject()).getResolveHelper()
        .resolveReferencedVariable(field.getName(), (PsiElement)reference);
      return !field.getManager().areElementsEquivalent(target, CompletionUtil.getOriginalOrSelf(field));
    }
    return false;
  }

  private static void qualifyFieldReference(InsertionContext context, PsiField field) {
    context.commitDocument();
    PsiFile file = context.getFile();
    final PsiReference reference = file.findReferenceAt(context.getStartOffset());
    if (reference instanceof PsiJavaCodeReferenceElement && ((PsiJavaCodeReferenceElement)reference).isQualified()) {
      return;
    }

    PsiClass containingClass = field.getContainingClass();
    if (containingClass != null && containingClass.getName() != null) {
      context.getDocument().insertString(context.getStartOffset(), ".");
      JavaCompletionUtil.insertClassReference(containingClass, file, context.getStartOffset());
      PsiDocumentManager.getInstance(context.getProject()).commitDocument(context.getDocument());
    }
  }
}
